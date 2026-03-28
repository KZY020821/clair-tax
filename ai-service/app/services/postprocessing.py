from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from functools import lru_cache
import logging
import math
from pathlib import Path
import re
from typing import Any

import joblib

from app.core.settings import Settings
from app.models.extraction import ExtractionWarning, NormalizedExtractionResult
from app.services.normalization import normalize_textract_expense_payload

LOGGER = logging.getLogger(__name__)

_TOTAL_FIELD_PRIORITY = {
    "TOTAL": 5,
    "TOTAL_AMOUNT": 4,
    "AMOUNT_DUE": 3,
    "SUBTOTAL": 2,
}
_DATE_FIELD_PRIORITY = {
    "INVOICE_RECEIPT_DATE": 4,
    "DATE": 3,
    "INVOICE_DATE": 2,
}
_MERCHANT_FIELD_PRIORITY = ("VENDOR_NAME", "RECEIVER_NAME")
_DATE_FORMATS: tuple[tuple[str, str], ...] = (
    ("%Y-%m-%d", "iso"),
    ("%d/%m/%Y", "slash"),
    ("%d-%m-%Y", "dash"),
    ("%d.%m.%Y", "dot"),
    ("%d %b %Y", "short_month"),
    ("%d %B %Y", "long_month"),
    ("%d/%m/%y", "slash_short_year"),
    ("%d-%m-%y", "dash_short_year"),
)


class ModelArtifactError(RuntimeError):
    pass


@dataclass(frozen=True)
class TextObservation:
    source: str
    text: str
    confidence: float
    field_type: str | None
    label_text: str | None
    x_position: float
    y_position: float
    page_number: int


@dataclass(frozen=True)
class AmountCandidate:
    candidate_id: str
    text: str
    normalized_value: Decimal
    confidence: float
    source: str
    field_type: str | None
    label_text: str | None
    x_position: float
    y_position: float
    has_currency_marker: bool
    has_total_keyword: bool
    has_subtotal_keyword: bool


@dataclass(frozen=True)
class DateCandidate:
    candidate_id: str
    text: str
    parsed_date: date
    confidence: float
    source: str
    field_type: str | None
    label_text: str | None
    x_position: float
    y_position: float
    has_date_keyword: bool
    pattern_name: str


@dataclass(frozen=True)
class CandidateBundle:
    provider_payload: dict[str, Any]
    observations: list[TextObservation]
    amount_candidates: list[AmountCandidate]
    date_candidates: list[DateCandidate]
    merchant_name: str | None
    merchant_confidence: float
    currency_code: str | None
    summary_field_count: int
    page_count: int

    @property
    def text_item_count(self) -> int:
        return len(self.observations)


@dataclass(frozen=True)
class ScoredCandidateSelection:
    candidate_id: str
    text: str
    score: float
    value: Decimal | date


@dataclass(frozen=True)
class ReceiptModelArtifact:
    schema_version: str
    artifact_version: str
    trained_at: str
    amount_candidate_model: Any
    date_candidate_model: Any
    receipt_validity_model: Any
    thresholds: dict[str, float]
    metrics: dict[str, float]


def extract_candidate_bundle(payload: dict[str, Any]) -> CandidateBundle:
    provider_payload = unwrap_provider_payload(payload)
    observations = _extract_observations(provider_payload)
    amount_candidates = _extract_amount_candidates(observations)
    date_candidates = _extract_date_candidates(observations)
    merchant_name, merchant_confidence = _pick_merchant_name(observations)
    currency_code = _pick_currency_code(provider_payload, observations)
    summary_field_count = _count_summary_fields(provider_payload)
    page_count = max(
        1,
        max((observation.page_number for observation in observations), default=1),
    )

    return CandidateBundle(
        provider_payload=provider_payload,
        observations=observations,
        amount_candidates=amount_candidates,
        date_candidates=date_candidates,
        merchant_name=merchant_name,
        merchant_confidence=merchant_confidence,
        currency_code=currency_code,
        summary_field_count=summary_field_count,
        page_count=page_count,
    )


def amount_candidate_feature_vector(
    candidate: AmountCandidate, bundle: CandidateBundle
) -> list[float]:
    return [
        _TOTAL_FIELD_PRIORITY.get(candidate.field_type or "", 0) / 5.0,
        _bool_as_float(candidate.has_total_keyword),
        _bool_as_float(candidate.has_subtotal_keyword),
        _bool_as_float(candidate.has_currency_marker),
        _clamp(candidate.confidence),
        _bool_as_float(candidate.source == "summary_field"),
        min(math.log10(float(candidate.normalized_value) + 1.0) / 6.0, 1.0),
        _clamp(candidate.y_position),
        _clamp(candidate.x_position),
        min(len(candidate.text) / 64.0, 1.0),
        min(bundle.summary_field_count / 40.0, 1.0),
        min(len(bundle.amount_candidates) / 20.0, 1.0),
    ]


def date_candidate_feature_vector(
    candidate: DateCandidate, bundle: CandidateBundle
) -> list[float]:
    return [
        _DATE_FIELD_PRIORITY.get(candidate.field_type or "", 0) / 4.0,
        _bool_as_float(candidate.has_date_keyword),
        _clamp(candidate.confidence),
        _bool_as_float(candidate.source == "summary_field"),
        _clamp((candidate.parsed_date.year - 2000) / 40.0),
        candidate.parsed_date.month / 12.0,
        candidate.parsed_date.day / 31.0,
        _clamp(candidate.y_position),
        _clamp(candidate.x_position),
        _date_pattern_rank(candidate.pattern_name) / 5.0,
        min(len(candidate.text) / 48.0, 1.0),
        min(len(bundle.date_candidates) / 20.0, 1.0),
    ]


def receipt_validity_feature_vector(
    bundle: CandidateBundle, mime_type: str
) -> list[float]:
    amount_baseline = baseline_amount_selection(bundle)
    date_baseline = baseline_date_selection(bundle)
    observation_confidences = [observation.confidence for observation in bundle.observations]
    average_confidence = (
        sum(observation_confidences) / len(observation_confidences)
        if observation_confidences
        else 0.0
    )
    return [
        min(bundle.text_item_count / 100.0, 1.0),
        min(bundle.summary_field_count / 40.0, 1.0),
        min(len(bundle.amount_candidates) / 20.0, 1.0),
        min(len(bundle.date_candidates) / 20.0, 1.0),
        _bool_as_float(bundle.merchant_name is not None),
        amount_baseline.score if amount_baseline is not None else 0.0,
        date_baseline.score if date_baseline is not None else 0.0,
        _clamp(average_confidence),
        _bool_as_float(bundle.currency_code is not None),
        _bool_as_float(mime_type.startswith("image/")),
        min(bundle.page_count / 5.0, 1.0),
        _bool_as_float(bundle.summary_field_count == 0),
    ]


def baseline_amount_selection(
    bundle: CandidateBundle,
) -> ScoredCandidateSelection | None:
    best_candidate: AmountCandidate | None = None
    best_score = -1.0
    for candidate in bundle.amount_candidates:
        score = _heuristic_amount_score(candidate)
        if score > best_score:
            best_candidate = candidate
            best_score = score

    if best_candidate is None:
        return None

    return ScoredCandidateSelection(
        candidate_id=best_candidate.candidate_id,
        text=best_candidate.text,
        score=round(best_score, 4),
        value=best_candidate.normalized_value,
    )


def baseline_date_selection(
    bundle: CandidateBundle,
) -> ScoredCandidateSelection | None:
    best_candidate: DateCandidate | None = None
    best_score = -1.0
    for candidate in bundle.date_candidates:
        score = _heuristic_date_score(candidate)
        if score > best_score:
            best_candidate = candidate
            best_score = score

    if best_candidate is None:
        return None

    return ScoredCandidateSelection(
        candidate_id=best_candidate.candidate_id,
        text=best_candidate.text,
        score=round(best_score, 4),
        value=best_candidate.parsed_date,
    )


def load_receipt_model_artifact(path: str) -> ReceiptModelArtifact:
    artifact_path = Path(path)
    if not artifact_path.exists():
        raise ModelArtifactError(f"Receipt model artifact was not found at {path}")

    stat = artifact_path.stat()
    return _load_receipt_model_artifact_cached(str(artifact_path), stat.st_mtime_ns)


@lru_cache(maxsize=4)
def _load_receipt_model_artifact_cached(
    artifact_path: str, modified_time_ns: int
) -> ReceiptModelArtifact:
    del modified_time_ns
    loaded = joblib.load(artifact_path)
    if not isinstance(loaded, dict):
        raise ModelArtifactError("Receipt model artifact payload is invalid")

    required_keys = {
        "schema_version",
        "artifact_version",
        "trained_at",
        "amount_candidate_model",
        "date_candidate_model",
        "receipt_validity_model",
        "thresholds",
        "metrics",
    }
    missing_keys = sorted(required_keys.difference(loaded.keys()))
    if missing_keys:
        raise ModelArtifactError(
            "Receipt model artifact is missing keys: " + ", ".join(missing_keys)
        )

    thresholds = loaded["thresholds"]
    if not isinstance(thresholds, dict):
        raise ModelArtifactError("Receipt model thresholds are invalid")

    return ReceiptModelArtifact(
        schema_version=str(loaded["schema_version"]),
        artifact_version=str(loaded["artifact_version"]),
        trained_at=str(loaded["trained_at"]),
        amount_candidate_model=loaded["amount_candidate_model"],
        date_candidate_model=loaded["date_candidate_model"],
        receipt_validity_model=loaded["receipt_validity_model"],
        thresholds={key: float(value) for key, value in thresholds.items()},
        metrics={
            key: float(value)
            for key, value in (loaded.get("metrics") or {}).items()
            if _is_number(value)
        },
    )


def postprocess_textract_expense_payload(
    *,
    receipt_id: str,
    payload: dict[str, Any],
    mime_type: str,
    settings: Settings,
) -> NormalizedExtractionResult:
    heuristic_result = normalize_textract_expense_payload(
        receipt_id=receipt_id,
        payload=unwrap_provider_payload(payload),
        settings=settings,
    )
    if not settings.trained_postprocessor_enabled:
        return heuristic_result

    artifact = load_receipt_model_artifact(settings.trained_postprocessor_artifact_path)
    bundle = extract_candidate_bundle(payload)
    amount_selection = _score_amount_candidates(bundle, artifact)
    date_selection = _score_date_candidates(bundle, artifact)
    validity_score = _score_validity(bundle, mime_type, artifact)

    LOGGER.info(
        "Receipt postprocessor inference receipt_id=%s artifact_version=%s validity_score=%.4f amount_score=%s date_score=%s",
        receipt_id,
        artifact.artifact_version,
        validity_score,
        None if amount_selection is None else round(amount_selection.score, 4),
        None if date_selection is None else round(date_selection.score, 4),
    )

    warnings = list(heuristic_result.warnings)
    selected_amount = (
        amount_selection.value
        if amount_selection is not None
        and amount_selection.score >= artifact.thresholds.get(
            "amount_selection_threshold", settings.amount_selection_threshold
        )
        else None
    )
    selected_date = (
        date_selection.value
        if date_selection is not None
        and date_selection.score
        >= artifact.thresholds.get(
            "date_selection_threshold", settings.date_selection_threshold
        )
        else None
    )

    if selected_amount is None:
        warnings.append(
            ExtractionWarning(
                code="amount_low_confidence",
                message="Amount candidates did not pass the trained selection threshold.",
            )
        )
    if selected_date is None:
        warnings.append(
            ExtractionWarning(
                code="date_low_confidence",
                message="Date candidates did not pass the trained selection threshold.",
            )
        )

    error_code, error_message = _classify_inference_error(
        bundle=bundle,
        validity_score=validity_score,
        selected_amount=selected_amount,
        selected_date=selected_date,
        validity_threshold=artifact.thresholds.get(
            "validity_threshold", settings.validity_threshold
        ),
    )
    confidence_score = _compute_trained_confidence(
        validity_score=validity_score,
        amount_score=None if amount_selection is None else amount_selection.score,
        date_score=None if date_selection is None else date_selection.score,
        merchant_confidence=bundle.merchant_confidence,
        warning_count=len(warnings),
        has_error=error_code is not None,
    )

    return heuristic_result.model_copy(
        update={
            "total_amount": selected_amount,
            "receipt_date": selected_date,
            "merchant_name": bundle.merchant_name or heuristic_result.merchant_name,
            "currency": bundle.currency_code or heuristic_result.currency,
            "confidence_score": confidence_score,
            "warnings": warnings,
            "raw_payload_json": _build_raw_payload(
                bundle=bundle,
                artifact=artifact,
                amount_selection=amount_selection,
                date_selection=date_selection,
                validity_score=validity_score,
                error_code=error_code,
                error_message=error_message,
            ),
            "provider_version": (
                f"{settings.textract_provider_version}+receipt-postprocessor:{artifact.artifact_version}"
            ),
            "error_code": error_code,
            "error_message": error_message,
        }
    )


def unwrap_provider_payload(payload: dict[str, Any]) -> dict[str, Any]:
    nested_payload = payload.get("provider_payload")
    if isinstance(nested_payload, dict):
        return nested_payload
    return payload


def _count_summary_fields(provider_payload: dict[str, Any]) -> int:
    expense_documents = provider_payload.get("ExpenseDocuments", [])
    if not expense_documents:
        return 0
    first_document = expense_documents[0] or {}
    summary_fields = first_document.get("SummaryFields", [])
    return len([field for field in summary_fields if isinstance(field, dict)])


def _extract_observations(provider_payload: dict[str, Any]) -> list[TextObservation]:
    observations: list[TextObservation] = []
    seen_keys: set[tuple[str, str, str | None, int, int]] = set()

    for source, field in _iter_expense_fields(provider_payload):
        text = _field_value_text(field)
        if text is None:
            continue
        observation = TextObservation(
            source=source,
            text=text,
            confidence=_field_confidence(field),
            field_type=_field_type(field),
            label_text=_field_label_text(field),
            x_position=_field_x_position(field),
            y_position=_field_y_position(field),
            page_number=_field_page_number(field),
        )
        dedupe_key = (
            observation.source,
            observation.text,
            observation.field_type,
            int(observation.x_position * 1000),
            int(observation.y_position * 1000),
        )
        if dedupe_key in seen_keys:
            continue
        seen_keys.add(dedupe_key)
        observations.append(observation)

    for line_block in provider_payload.get("Blocks", []):
        if not isinstance(line_block, dict):
            continue
        if line_block.get("BlockType") != "LINE":
            continue
        text = line_block.get("Text")
        if not isinstance(text, str) or not text.strip():
            continue
        observation = TextObservation(
            source="block_line",
            text=text.strip(),
            confidence=_clamp(float(line_block.get("Confidence", 0.0)) / 100.0),
            field_type=None,
            label_text=None,
            x_position=_geometry_coordinate(line_block, "Left"),
            y_position=_geometry_coordinate(line_block, "Top"),
            page_number=int(line_block.get("Page", 1) or 1),
        )
        dedupe_key = (
            observation.source,
            observation.text,
            observation.field_type,
            int(observation.x_position * 1000),
            int(observation.y_position * 1000),
        )
        if dedupe_key in seen_keys:
            continue
        seen_keys.add(dedupe_key)
        observations.append(observation)

    return observations


def _iter_expense_fields(provider_payload: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
    yielded_fields: list[tuple[str, dict[str, Any]]] = []
    for expense_document in provider_payload.get("ExpenseDocuments", []):
        if not isinstance(expense_document, dict):
            continue
        for summary_field in expense_document.get("SummaryFields", []):
            if isinstance(summary_field, dict):
                yielded_fields.append(("summary_field", summary_field))
        for line_item_group in expense_document.get("LineItemGroups", []):
            if not isinstance(line_item_group, dict):
                continue
            for line_item in line_item_group.get("LineItems", []):
                if not isinstance(line_item, dict):
                    continue
                for expense_field in line_item.get("LineItemExpenseFields", []):
                    if isinstance(expense_field, dict):
                        yielded_fields.append(("line_item_field", expense_field))
    return yielded_fields


def _extract_amount_candidates(observations: list[TextObservation]) -> list[AmountCandidate]:
    candidates: list[AmountCandidate] = []
    for index, observation in enumerate(observations):
        allow_integer = (
            _TOTAL_FIELD_PRIORITY.get(observation.field_type or "", 0) > 0
            or _contains_total_keyword(_combined_label_text(observation))
            or _contains_currency_marker(observation.text)
        )
        parsed_amount = _parse_decimal(observation.text, allow_integer=allow_integer)
        if parsed_amount is None:
            continue

        candidates.append(
            AmountCandidate(
                candidate_id=f"amount-{index}",
                text=observation.text,
                normalized_value=parsed_amount,
                confidence=observation.confidence,
                source=observation.source,
                field_type=observation.field_type,
                label_text=observation.label_text,
                x_position=observation.x_position,
                y_position=observation.y_position,
                has_currency_marker=_contains_currency_marker(observation.text),
                has_total_keyword=_contains_total_keyword(
                    _combined_label_text(observation)
                ),
                has_subtotal_keyword=_contains_subtotal_keyword(
                    _combined_label_text(observation)
                ),
            )
        )
    return candidates


def _extract_date_candidates(observations: list[TextObservation]) -> list[DateCandidate]:
    candidates: list[DateCandidate] = []
    for index, observation in enumerate(observations):
        parsed = _parse_date(observation.text)
        if parsed is None:
            continue
        parsed_date, pattern_name = parsed
        candidates.append(
            DateCandidate(
                candidate_id=f"date-{index}",
                text=observation.text,
                parsed_date=parsed_date,
                confidence=observation.confidence,
                source=observation.source,
                field_type=observation.field_type,
                label_text=observation.label_text,
                x_position=observation.x_position,
                y_position=observation.y_position,
                has_date_keyword=_contains_date_keyword(_combined_label_text(observation)),
                pattern_name=pattern_name,
            )
        )
    return candidates


def _pick_merchant_name(
    observations: list[TextObservation],
) -> tuple[str | None, float]:
    for field_type in _MERCHANT_FIELD_PRIORITY:
        for observation in observations:
            if observation.field_type != field_type:
                continue
            if observation.text:
                return observation.text.strip(), observation.confidence
    return None, 0.0


def _pick_currency_code(
    provider_payload: dict[str, Any], observations: list[TextObservation]
) -> str | None:
    for expense_document in provider_payload.get("ExpenseDocuments", []):
        if not isinstance(expense_document, dict):
            continue
        for summary_field in expense_document.get("SummaryFields", []):
            if not isinstance(summary_field, dict):
                continue
            currency = summary_field.get("Currency", {})
            if isinstance(currency, dict):
                code = currency.get("Code")
                if isinstance(code, str) and code.strip():
                    return code.strip().upper()

    for observation in observations:
        if "MYR" in observation.text.upper() or "RM" in observation.text.upper():
            return "MYR"
    return None


def _score_amount_candidates(
    bundle: CandidateBundle, artifact: ReceiptModelArtifact
) -> ScoredCandidateSelection | None:
    if not bundle.amount_candidates:
        return None

    features = [
        amount_candidate_feature_vector(candidate, bundle)
        for candidate in bundle.amount_candidates
    ]
    probabilities = artifact.amount_candidate_model.predict_proba(features)
    best_candidate: AmountCandidate | None = None
    best_score = -1.0
    for candidate, probability_row in zip(bundle.amount_candidates, probabilities, strict=True):
        score = _positive_probability(probability_row)
        if score > best_score:
            best_candidate = candidate
            best_score = score

    if best_candidate is None:
        return None

    return ScoredCandidateSelection(
        candidate_id=best_candidate.candidate_id,
        text=best_candidate.text,
        score=round(best_score, 4),
        value=best_candidate.normalized_value,
    )


def _score_date_candidates(
    bundle: CandidateBundle, artifact: ReceiptModelArtifact
) -> ScoredCandidateSelection | None:
    if not bundle.date_candidates:
        return None

    features = [
        date_candidate_feature_vector(candidate, bundle)
        for candidate in bundle.date_candidates
    ]
    probabilities = artifact.date_candidate_model.predict_proba(features)
    best_candidate: DateCandidate | None = None
    best_score = -1.0
    for candidate, probability_row in zip(bundle.date_candidates, probabilities, strict=True):
        score = _positive_probability(probability_row)
        if score > best_score:
            best_candidate = candidate
            best_score = score

    if best_candidate is None:
        return None

    return ScoredCandidateSelection(
        candidate_id=best_candidate.candidate_id,
        text=best_candidate.text,
        score=round(best_score, 4),
        value=best_candidate.parsed_date,
    )


def _score_validity(
    bundle: CandidateBundle, mime_type: str, artifact: ReceiptModelArtifact
) -> float:
    probability_row = artifact.receipt_validity_model.predict_proba(
        [receipt_validity_feature_vector(bundle, mime_type)]
    )[0]
    return round(_positive_probability(probability_row), 4)


def _classify_inference_error(
    *,
    bundle: CandidateBundle,
    validity_score: float,
    selected_amount: Decimal | None,
    selected_date: date | None,
    validity_threshold: float,
) -> tuple[str | None, str | None]:
    if bundle.text_item_count == 0:
        return (
            "unreadable_receipt",
            "The uploaded file did not contain readable receipt text.",
        )
    if validity_score < validity_threshold:
        if not bundle.amount_candidates and not bundle.date_candidates:
            return (
                "not_a_receipt",
                "The uploaded file does not look like a valid receipt.",
            )
        return (
            "low_confidence_extraction",
            "The receipt content was detected, but extraction confidence stayed below the review threshold.",
        )
    if selected_amount is None:
        return (
            "amount_not_found",
            "A receipt was detected, but the total amount could not be extracted confidently.",
        )
    if selected_date is None:
        return (
            "date_not_found",
            "A receipt was detected, but the receipt date could not be extracted confidently.",
        )
    return None, None


def _compute_trained_confidence(
    *,
    validity_score: float,
    amount_score: float | None,
    date_score: float | None,
    merchant_confidence: float,
    warning_count: int,
    has_error: bool,
) -> Decimal:
    result = (
        Decimal(str(validity_score)) * Decimal("0.35")
        + Decimal(str(amount_score or 0.0)) * Decimal("0.35")
        + Decimal(str(date_score or 0.0)) * Decimal("0.20")
        + Decimal(str(merchant_confidence)) * Decimal("0.10")
    )
    result -= Decimal("0.05") * Decimal(warning_count)
    if has_error:
        result -= Decimal("0.20")
    if result < 0:
        result = Decimal("0")
    if result > 1:
        result = Decimal("1")
    return result.quantize(Decimal("0.0001"))


def _build_raw_payload(
    *,
    bundle: CandidateBundle,
    artifact: ReceiptModelArtifact,
    amount_selection: ScoredCandidateSelection | None,
    date_selection: ScoredCandidateSelection | None,
    validity_score: float,
    error_code: str | None,
    error_message: str | None,
) -> dict[str, Any]:
    return {
        "provider_payload": bundle.provider_payload,
        "postprocessor": {
            "artifact_version": artifact.artifact_version,
            "validity_score": round(validity_score, 4),
            "amount_selection_threshold": artifact.thresholds.get(
                "amount_selection_threshold"
            ),
            "date_selection_threshold": artifact.thresholds.get(
                "date_selection_threshold"
            ),
            "validity_threshold": artifact.thresholds.get("validity_threshold"),
            "amount_candidate_count": len(bundle.amount_candidates),
            "date_candidate_count": len(bundle.date_candidates),
            "selected_amount_candidate": _serialize_selection(amount_selection),
            "selected_date_candidate": _serialize_selection(date_selection),
            "error_code": error_code,
            "error_message": error_message,
        },
    }


def _serialize_selection(
    selection: ScoredCandidateSelection | None,
) -> dict[str, Any] | None:
    if selection is None:
        return None
    value = selection.value
    if isinstance(value, Decimal):
        serialized_value: str | None = str(value)
    elif isinstance(value, date):
        serialized_value = value.isoformat()
    else:
        serialized_value = str(value)
    return {
        "candidate_id": selection.candidate_id,
        "text": selection.text,
        "score": round(selection.score, 4),
        "value": serialized_value,
    }


def _heuristic_amount_score(candidate: AmountCandidate) -> float:
    score = 0.0
    score += _TOTAL_FIELD_PRIORITY.get(candidate.field_type or "", 0) * 0.10
    score += 0.20 if candidate.has_total_keyword else 0.0
    score -= 0.08 if candidate.has_subtotal_keyword else 0.0
    score += 0.08 if candidate.has_currency_marker else 0.0
    score += 0.18 if candidate.source == "summary_field" else 0.0
    score += candidate.confidence * 0.24
    return _clamp(score)


def _heuristic_date_score(candidate: DateCandidate) -> float:
    score = 0.0
    score += _DATE_FIELD_PRIORITY.get(candidate.field_type or "", 0) * 0.12
    score += 0.18 if candidate.has_date_keyword else 0.0
    score += 0.14 if candidate.source == "summary_field" else 0.0
    score += candidate.confidence * 0.30
    score += _date_pattern_rank(candidate.pattern_name) * 0.05
    return _clamp(score)


def _field_type(field: dict[str, Any]) -> str | None:
    field_type = field.get("Type", {})
    if not isinstance(field_type, dict):
        return None
    value = field_type.get("Text")
    return value.strip().upper() if isinstance(value, str) and value.strip() else None


def _field_label_text(field: dict[str, Any]) -> str | None:
    label_detection = field.get("LabelDetection", {})
    if not isinstance(label_detection, dict):
        return None
    text = label_detection.get("Text")
    return text.strip() if isinstance(text, str) and text.strip() else None


def _field_value_text(field: dict[str, Any]) -> str | None:
    value_detection = field.get("ValueDetection", {})
    if not isinstance(value_detection, dict):
        return None
    text = value_detection.get("Text")
    return text.strip() if isinstance(text, str) and text.strip() else None


def _field_confidence(field: dict[str, Any]) -> float:
    value_detection = field.get("ValueDetection", {})
    if not isinstance(value_detection, dict):
        return 0.0
    raw_confidence = value_detection.get("Confidence")
    if raw_confidence is None:
        return 0.0
    try:
        return _clamp(float(raw_confidence) / 100.0)
    except (TypeError, ValueError):
        return 0.0


def _field_page_number(field: dict[str, Any]) -> int:
    raw_page_number = field.get("PageNumber")
    try:
        return int(raw_page_number)
    except (TypeError, ValueError):
        return 1


def _field_x_position(field: dict[str, Any]) -> float:
    return _geometry_coordinate(field.get("ValueDetection", {}) or field, "Left")


def _field_y_position(field: dict[str, Any]) -> float:
    return _geometry_coordinate(field.get("ValueDetection", {}) or field, "Top")


def _geometry_coordinate(container: dict[str, Any], coordinate_name: str) -> float:
    geometry = container.get("Geometry", {})
    if not isinstance(geometry, dict):
        return 0.0
    bounding_box = geometry.get("BoundingBox", {})
    if not isinstance(bounding_box, dict):
        return 0.0
    value = bounding_box.get(coordinate_name)
    try:
        return _clamp(float(value))
    except (TypeError, ValueError):
        return 0.0


def _parse_decimal(text: str, *, allow_integer: bool) -> Decimal | None:
    uppercase_text = text.upper()
    if _looks_like_date_text(uppercase_text) and not _contains_currency_marker(uppercase_text):
        return None

    matches = re.findall(
        r"(?<!\d)(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{2})?(?!\d)",
        uppercase_text.replace("RM", " ").replace("MYR", " "),
    )
    if not matches:
        return None

    for raw_match in reversed(matches):
        if "." not in raw_match and not allow_integer:
            continue
        normalized = raw_match.replace(",", "")
        try:
            value = Decimal(normalized).quantize(Decimal("0.01"))
        except Exception:
            continue
        if value < 0:
            continue
        return value
    return None


def _parse_date(text: str) -> tuple[date, str] | None:
    for token in _candidate_date_tokens(text):
        cleaned_token = token.replace(",", " ").strip()
        for date_format, pattern_name in _DATE_FORMATS:
            try:
                return datetime.strptime(cleaned_token, date_format).date(), pattern_name
            except ValueError:
                continue
    return None


def _candidate_date_tokens(text: str) -> list[str]:
    patterns = (
        r"\b\d{4}-\d{2}-\d{2}\b",
        r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b",
        r"\b\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}\b",
        r"\b\d{1,2}\.\d{1,2}\.\d{2,4}\b",
    )
    tokens: list[str] = []
    for pattern in patterns:
        tokens.extend(match.group(0) for match in re.finditer(pattern, text))
    return tokens


def _combined_label_text(observation: TextObservation) -> str:
    combined = " ".join(
        value
        for value in (observation.field_type, observation.label_text, observation.text)
        if value
    )
    return combined.lower()


def _contains_currency_marker(text: str) -> bool:
    uppercase_text = text.upper()
    return "MYR" in uppercase_text or re.search(r"\bRM\b", uppercase_text) is not None


def _contains_total_keyword(text: str) -> bool:
    return any(keyword in text for keyword in ("total", "jumlah", "amount due", "balance due"))


def _contains_subtotal_keyword(text: str) -> bool:
    return "subtotal" in text or "sub total" in text


def _contains_date_keyword(text: str) -> bool:
    return any(keyword in text for keyword in ("date", "tarikh", "invoice date", "receipt date"))


def _looks_like_date_text(text: str) -> bool:
    return any(
        re.search(pattern, text) is not None
        for pattern in (
            r"\b\d{4}-\d{2}-\d{2}\b",
            r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b",
            r"\b\d{1,2}\.\d{1,2}\.\d{2,4}\b",
        )
    )


def _positive_probability(probability_row: Any) -> float:
    if isinstance(probability_row, (list, tuple)):
        if not probability_row:
            return 0.0
        return _clamp(float(probability_row[-1]))

    row_values = list(probability_row)
    if not row_values:
        return 0.0
    return _clamp(float(row_values[-1]))


def _date_pattern_rank(pattern_name: str) -> int:
    return {
        "iso": 5,
        "slash": 4,
        "dash": 4,
        "dot": 3,
        "short_month": 4,
        "long_month": 4,
        "slash_short_year": 2,
        "dash_short_year": 2,
    }.get(pattern_name, 1)


def _bool_as_float(value: bool) -> float:
    return 1.0 if value else 0.0


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _is_number(value: Any) -> bool:
    return isinstance(value, int | float)
