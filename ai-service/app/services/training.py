from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from decimal import Decimal
import json
import logging
from pathlib import Path
import random
from typing import Any

import joblib
import numpy
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix, precision_score, recall_score

from app.core.settings import Settings
from app.models.training import ReceiptDatasetManifestEntry
from app.services.normalization import normalize_textract_expense_payload
from app.services.postprocessing import (
    ReceiptModelArtifact,
    amount_candidate_feature_vector,
    date_candidate_feature_vector,
    extract_candidate_bundle,
    receipt_validity_feature_vector,
    unwrap_provider_payload,
)

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class CandidateTrainingRow:
    receipt_id: str
    label: int
    value: str
    features: list[float]


@dataclass(frozen=True)
class ValidityTrainingRow:
    receipt_id: str
    label: int
    features: list[float]


@dataclass(frozen=True)
class ReceiptTrainingDataset:
    manifest_entries: list[ReceiptDatasetManifestEntry]
    amount_rows: list[CandidateTrainingRow]
    date_rows: list[CandidateTrainingRow]
    validity_rows: list[ValidityTrainingRow]
    skipped_amount_receipt_ids: list[str]
    skipped_date_receipt_ids: list[str]


def load_manifest_entries(path: str | Path) -> list[ReceiptDatasetManifestEntry]:
    manifest_path = Path(path)
    raw_payload = manifest_path.read_text(encoding="utf-8")
    parsed_entries: list[Any]
    if manifest_path.suffix == ".jsonl":
        parsed_entries = [
            json.loads(line)
            for line in raw_payload.splitlines()
            if line.strip()
        ]
    else:
        parsed_entries = json.loads(raw_payload)
        if not isinstance(parsed_entries, list):
            raise ValueError("Manifest JSON must contain a list of entries")

    entries = [ReceiptDatasetManifestEntry.model_validate(item) for item in parsed_entries]
    validate_labeled_manifest_entries(entries)
    return entries


def write_manifest_entries(
    path: str | Path, entries: list[ReceiptDatasetManifestEntry]
) -> None:
    manifest_path = Path(path)
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    content = "\n".join(entry.model_dump_json() for entry in entries)
    manifest_path.write_text(content + "\n", encoding="utf-8")


def scaffold_annotation_manifest(
    input_dir: str | Path,
    output_path: str | Path,
    *,
    annotation_source: str = "sample-pack",
) -> int:
    supported_suffixes = {".pdf", ".png", ".jpg", ".jpeg", ".webp"}
    files = sorted(
        path
        for path in Path(input_dir).rglob("*")
        if path.is_file() and path.suffix.lower() in supported_suffixes
    )
    entries = [
        ReceiptDatasetManifestEntry(
            receipt_id=path.stem,
            file_path=str(path.resolve()),
            mime_type=_guess_mime_type(path.suffix.lower()),
            annotation_source=annotation_source,
        )
        for path in files
    ]
    write_manifest_entries(output_path, entries)
    return len(entries)


def validate_labeled_manifest_entries(
    entries: list[ReceiptDatasetManifestEntry],
) -> None:
    seen_receipt_ids: set[str] = set()
    for entry in entries:
        if entry.receipt_id in seen_receipt_ids:
            raise ValueError(f"Duplicate receipt_id detected: {entry.receipt_id}")
        seen_receipt_ids.add(entry.receipt_id)

        if entry.file_path is None and entry.s3_key is None:
            raise ValueError(
                f"Manifest row {entry.receipt_id} requires file_path or s3_key"
            )
        if entry.raw_payload_json is None and entry.ocr_payload_path is None:
            raise ValueError(
                f"Manifest row {entry.receipt_id} requires raw_payload_json or ocr_payload_path"
            )
        if entry.is_valid_receipt is None:
            raise ValueError(
                f"Manifest row {entry.receipt_id} requires is_valid_receipt"
            )
        if entry.is_valid_receipt:
            if entry.correct_total_amount is None:
                raise ValueError(
                    f"Manifest row {entry.receipt_id} requires correct_total_amount"
                )
            if entry.correct_receipt_date is None:
                raise ValueError(
                    f"Manifest row {entry.receipt_id} requires correct_receipt_date"
                )
        else:
            if not entry.invalid_reason:
                raise ValueError(
                    f"Manifest row {entry.receipt_id} requires invalid_reason"
                )


def split_manifest_entries(
    entries: list[ReceiptDatasetManifestEntry],
    *,
    holdout_ratio: float = 0.2,
    seed: int = 42,
) -> tuple[list[ReceiptDatasetManifestEntry], list[ReceiptDatasetManifestEntry]]:
    receipt_ids = sorted({entry.receipt_id for entry in entries})
    rng = random.Random(seed)
    rng.shuffle(receipt_ids)
    holdout_count = max(1, int(round(len(receipt_ids) * holdout_ratio)))
    if holdout_count >= len(receipt_ids):
        holdout_count = max(1, len(receipt_ids) - 1)
    holdout_receipt_ids = set(receipt_ids[:holdout_count])
    train_entries = [
        entry for entry in entries if entry.receipt_id not in holdout_receipt_ids
    ]
    holdout_entries = [
        entry for entry in entries if entry.receipt_id in holdout_receipt_ids
    ]
    return train_entries, holdout_entries


def build_training_dataset(
    entries: list[ReceiptDatasetManifestEntry],
) -> ReceiptTrainingDataset:
    validate_labeled_manifest_entries(entries)
    amount_rows: list[CandidateTrainingRow] = []
    date_rows: list[CandidateTrainingRow] = []
    validity_rows: list[ValidityTrainingRow] = []
    skipped_amount_receipt_ids: list[str] = []
    skipped_date_receipt_ids: list[str] = []

    for entry in entries:
        payload = _load_payload(entry)
        bundle = extract_candidate_bundle(payload)
        validity_rows.append(
            ValidityTrainingRow(
                receipt_id=entry.receipt_id,
                label=1 if entry.is_valid_receipt else 0,
                features=receipt_validity_feature_vector(bundle, entry.mime_type),
            )
        )

        if not entry.is_valid_receipt:
            continue

        expected_amount = entry.correct_total_amount
        expected_date = entry.correct_receipt_date
        assert expected_amount is not None
        assert expected_date is not None

        receipt_amount_rows = [
            CandidateTrainingRow(
                receipt_id=entry.receipt_id,
                label=1 if candidate.normalized_value == expected_amount else 0,
                value=str(candidate.normalized_value),
                features=amount_candidate_feature_vector(candidate, bundle),
            )
            for candidate in bundle.amount_candidates
        ]
        if any(row.label == 1 for row in receipt_amount_rows):
            amount_rows.extend(receipt_amount_rows)
        else:
            skipped_amount_receipt_ids.append(entry.receipt_id)

        receipt_date_rows = [
            CandidateTrainingRow(
                receipt_id=entry.receipt_id,
                label=1 if candidate.parsed_date == expected_date else 0,
                value=candidate.parsed_date.isoformat(),
                features=date_candidate_feature_vector(candidate, bundle),
            )
            for candidate in bundle.date_candidates
        ]
        if any(row.label == 1 for row in receipt_date_rows):
            date_rows.extend(receipt_date_rows)
        else:
            skipped_date_receipt_ids.append(entry.receipt_id)

    return ReceiptTrainingDataset(
        manifest_entries=entries,
        amount_rows=amount_rows,
        date_rows=date_rows,
        validity_rows=validity_rows,
        skipped_amount_receipt_ids=skipped_amount_receipt_ids,
        skipped_date_receipt_ids=skipped_date_receipt_ids,
    )


def write_training_tables(
    dataset: ReceiptTrainingDataset, output_dir: str | Path
) -> dict[str, str]:
    target_dir = Path(output_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    amount_path = target_dir / "amount_candidates.jsonl"
    date_path = target_dir / "date_candidates.jsonl"
    validity_path = target_dir / "receipt_validity.jsonl"

    amount_path.write_text(
        "\n".join(json.dumps(asdict(row)) for row in dataset.amount_rows) + "\n",
        encoding="utf-8",
    )
    date_path.write_text(
        "\n".join(json.dumps(asdict(row)) for row in dataset.date_rows) + "\n",
        encoding="utf-8",
    )
    validity_path.write_text(
        "\n".join(json.dumps(asdict(row)) for row in dataset.validity_rows) + "\n",
        encoding="utf-8",
    )

    return {
        "amount_candidates": str(amount_path),
        "date_candidates": str(date_path),
        "receipt_validity": str(validity_path),
    }


def train_receipt_models(
    entries: list[ReceiptDatasetManifestEntry],
    *,
    output_path: str | Path,
    settings: Settings | None = None,
    holdout_ratio: float = 0.2,
    seed: int = 42,
    require_beats_baseline: bool = False,
) -> dict[str, Any]:
    resolved_settings = settings or Settings()
    if len(entries) < 4:
        raise ValueError("At least four labeled receipts are required to train models")

    train_entries, holdout_entries = split_manifest_entries(
        entries, holdout_ratio=holdout_ratio, seed=seed
    )
    train_dataset = build_training_dataset(train_entries)
    if not train_dataset.amount_rows:
        raise ValueError("Training data did not contain amount candidates with positives")
    if not train_dataset.date_rows:
        raise ValueError("Training data did not contain date candidates with positives")
    if not train_dataset.validity_rows:
        raise ValueError("Training data did not contain receipt validity examples")

    amount_model = _fit_binary_classifier(train_dataset.amount_rows, random_state=seed)
    date_model = _fit_binary_classifier(train_dataset.date_rows, random_state=seed + 1)
    validity_model = _fit_binary_classifier(
        train_dataset.validity_rows, random_state=seed + 2
    )

    artifact_version = datetime.now(UTC).strftime("%Y%m%d%H%M%S")
    thresholds = {
        "amount_selection_threshold": resolved_settings.amount_selection_threshold,
        "date_selection_threshold": resolved_settings.date_selection_threshold,
        "validity_threshold": resolved_settings.validity_threshold,
    }
    artifact_payload = {
        "schema_version": "2026-03-27",
        "artifact_version": artifact_version,
        "trained_at": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "amount_candidate_model": amount_model,
        "date_candidate_model": date_model,
        "receipt_validity_model": validity_model,
        "thresholds": thresholds,
        "metrics": {},
    }
    artifact = ReceiptModelArtifact(
        schema_version=artifact_payload["schema_version"],
        artifact_version=artifact_payload["artifact_version"],
        trained_at=artifact_payload["trained_at"],
        amount_candidate_model=amount_model,
        date_candidate_model=date_model,
        receipt_validity_model=validity_model,
        thresholds=thresholds,
        metrics={},
    )
    metrics = evaluate_artifact_on_entries(
        artifact,
        holdout_entries,
        settings=resolved_settings,
    )
    artifact_payload["metrics"] = metrics

    if require_beats_baseline:
        if metrics["amount_selection_accuracy"] <= metrics["baseline_amount_selection_accuracy"]:
            raise ValueError("Trained amount selector did not beat the heuristic baseline")
        if metrics["date_selection_accuracy"] <= metrics["baseline_date_selection_accuracy"]:
            raise ValueError("Trained date selector did not beat the heuristic baseline")
        if metrics["validity_precision"] < metrics["baseline_validity_precision"]:
            raise ValueError("Trained validity classifier regressed against the heuristic baseline")

    target_path = Path(output_path)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact_payload, target_path)

    return {
        "artifact_path": str(target_path),
        "artifact_version": artifact_version,
        "train_receipt_count": len(train_entries),
        "holdout_receipt_count": len(holdout_entries),
        "feature_flag_recommended": len(entries)
        >= resolved_settings.minimum_seed_dataset_size,
        "metrics": metrics,
        "skipped_amount_receipts": train_dataset.skipped_amount_receipt_ids,
        "skipped_date_receipts": train_dataset.skipped_date_receipt_ids,
    }


def evaluate_artifact_on_entries(
    artifact: ReceiptModelArtifact,
    entries: list[ReceiptDatasetManifestEntry],
    *,
    settings: Settings | None = None,
) -> dict[str, float]:
    resolved_settings = settings or Settings()
    y_true_validity: list[int] = []
    y_pred_validity: list[int] = []
    y_pred_baseline_validity: list[int] = []
    valid_receipt_count = 0
    amount_correct = 0
    baseline_amount_correct = 0
    date_correct = 0
    baseline_date_correct = 0
    combined_correct = 0
    baseline_combined_correct = 0

    for entry in entries:
        payload = _load_payload(entry)
        bundle = extract_candidate_bundle(payload)
        amount_selection = _predict_amount(bundle, artifact)
        date_selection = _predict_date(bundle, artifact)
        validity_score = artifact.receipt_validity_model.predict_proba(
            [receipt_validity_feature_vector(bundle, entry.mime_type)]
        )[0]
        validity_probability = float(validity_score[-1])
        predicted_valid = (
            validity_probability
            >= artifact.thresholds["validity_threshold"]
        )
        y_true_validity.append(1 if entry.is_valid_receipt else 0)
        y_pred_validity.append(1 if predicted_valid else 0)

        heuristic_result = normalize_textract_expense_payload(
            receipt_id=entry.receipt_id,
            payload=unwrap_provider_payload(payload),
            settings=resolved_settings,
        )
        baseline_valid = (
            heuristic_result.total_amount is not None
            and heuristic_result.receipt_date is not None
            and heuristic_result.confidence_score >= Decimal("0.55")
        )
        y_pred_baseline_validity.append(1 if baseline_valid else 0)

        if not entry.is_valid_receipt:
            continue

        valid_receipt_count += 1
        expected_amount = entry.correct_total_amount
        expected_date = entry.correct_receipt_date
        assert expected_amount is not None
        assert expected_date is not None

        amount_is_correct = amount_selection == expected_amount
        date_is_correct = date_selection == expected_date
        baseline_amount_is_correct = heuristic_result.total_amount == expected_amount
        baseline_date_is_correct = heuristic_result.receipt_date == expected_date

        amount_correct += 1 if amount_is_correct else 0
        date_correct += 1 if date_is_correct else 0
        baseline_amount_correct += 1 if baseline_amount_is_correct else 0
        baseline_date_correct += 1 if baseline_date_is_correct else 0
        combined_correct += 1 if amount_is_correct and date_is_correct else 0
        baseline_combined_correct += (
            1 if baseline_amount_is_correct and baseline_date_is_correct else 0
        )

    cm = confusion_matrix(y_true_validity, y_pred_validity, labels=[0, 1])
    baseline_cm = confusion_matrix(
        y_true_validity, y_pred_baseline_validity, labels=[0, 1]
    )
    metrics = {
        "validity_precision": float(
            precision_score(y_true_validity, y_pred_validity, zero_division=0)
        ),
        "validity_recall": float(
            recall_score(y_true_validity, y_pred_validity, zero_division=0)
        ),
        "baseline_validity_precision": float(
            precision_score(y_true_validity, y_pred_baseline_validity, zero_division=0)
        ),
        "baseline_validity_recall": float(
            recall_score(y_true_validity, y_pred_baseline_validity, zero_division=0)
        ),
        "validity_true_negative": float(cm[0, 0]),
        "validity_false_positive": float(cm[0, 1]),
        "validity_false_negative": float(cm[1, 0]),
        "validity_true_positive": float(cm[1, 1]),
        "baseline_validity_true_negative": float(baseline_cm[0, 0]),
        "baseline_validity_false_positive": float(baseline_cm[0, 1]),
        "baseline_validity_false_negative": float(baseline_cm[1, 0]),
        "baseline_validity_true_positive": float(baseline_cm[1, 1]),
        "amount_selection_accuracy": _safe_ratio(amount_correct, valid_receipt_count),
        "date_selection_accuracy": _safe_ratio(date_correct, valid_receipt_count),
        "combined_extraction_accuracy": _safe_ratio(
            combined_correct, valid_receipt_count
        ),
        "baseline_amount_selection_accuracy": _safe_ratio(
            baseline_amount_correct, valid_receipt_count
        ),
        "baseline_date_selection_accuracy": _safe_ratio(
            baseline_date_correct, valid_receipt_count
        ),
        "baseline_combined_extraction_accuracy": _safe_ratio(
            baseline_combined_correct, valid_receipt_count
        ),
    }
    return metrics


def _fit_binary_classifier(
    rows: list[CandidateTrainingRow] | list[ValidityTrainingRow], *, random_state: int
) -> HistGradientBoostingClassifier:
    labels = [row.label for row in rows]
    if len(set(labels)) < 2:
        raise ValueError("Training rows must include both positive and negative labels")
    features = numpy.asarray([row.features for row in rows], dtype=float)
    model = HistGradientBoostingClassifier(
        random_state=random_state,
        max_depth=4,
        learning_rate=0.1,
        max_iter=200,
    )
    model.fit(features, labels)
    return model


def _predict_amount(
    bundle, artifact: ReceiptModelArtifact
) -> Decimal | None:
    amount_selection = _select_candidate(
        candidates=bundle.amount_candidates,
        feature_builder=amount_candidate_feature_vector,
        bundle=bundle,
        model=artifact.amount_candidate_model,
        threshold=artifact.thresholds["amount_selection_threshold"],
        value_getter=lambda candidate: candidate.normalized_value,
    )
    return amount_selection


def _predict_date(
    bundle, artifact: ReceiptModelArtifact
) -> Any:
    return _select_candidate(
        candidates=bundle.date_candidates,
        feature_builder=date_candidate_feature_vector,
        bundle=bundle,
        model=artifact.date_candidate_model,
        threshold=artifact.thresholds["date_selection_threshold"],
        value_getter=lambda candidate: candidate.parsed_date,
    )


def _select_candidate(
    *,
    candidates: list[Any],
    feature_builder,
    bundle: Any,
    model: Any,
    threshold: float,
    value_getter,
) -> Any:
    if not candidates:
        return None
    feature_rows = [feature_builder(candidate, bundle) for candidate in candidates]
    probabilities = model.predict_proba(feature_rows)
    best_index = -1
    best_probability = -1.0
    for index, probability_row in enumerate(probabilities):
        positive_probability = float(probability_row[-1])
        if positive_probability > best_probability:
            best_index = index
            best_probability = positive_probability
    if best_index < 0 or best_probability < threshold:
        return None
    return value_getter(candidates[best_index])


def _load_payload(entry: ReceiptDatasetManifestEntry) -> dict[str, Any]:
    if entry.raw_payload_json is not None:
        return entry.raw_payload_json
    assert entry.ocr_payload_path is not None
    payload_path = Path(entry.ocr_payload_path)
    raw_content = payload_path.read_text(encoding="utf-8")
    return json.loads(raw_content)


def _guess_mime_type(suffix: str) -> str:
    return {
        ".pdf": "application/pdf",
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".webp": "image/webp",
    }.get(suffix, "application/octet-stream")


def _safe_ratio(numerator: int, denominator: int) -> float:
    if denominator <= 0:
        return 0.0
    return round(numerator / denominator, 4)
