from __future__ import annotations

from datetime import UTC, date, datetime
from decimal import Decimal
from typing import Any

from app.core.settings import Settings
from app.models.extraction import ExtractionWarning, NormalizedExtractionResult


def normalize_textract_expense_payload(
    *,
    receipt_id: str,
    payload: dict[str, Any],
    settings: Settings,
) -> NormalizedExtractionResult:
    summary_fields = _extract_summary_fields(payload)
    warnings: list[ExtractionWarning] = []

    total_amount, amount_confidence = _pick_total_amount(summary_fields)
    if total_amount is None:
        warnings.append(
            ExtractionWarning(
                code="total_missing",
                message="Total amount was not confidently detected from the receipt.",
            )
        )

    receipt_date, date_confidence = _pick_receipt_date(summary_fields)
    if receipt_date is None:
        warnings.append(
            ExtractionWarning(
                code="date_missing",
                message="Receipt date was not confidently detected from the receipt.",
            )
        )

    merchant_name, merchant_confidence = _pick_merchant_name(summary_fields)
    if merchant_name is None:
        warnings.append(
            ExtractionWarning(
                code="merchant_missing",
                message="Merchant name was not confidently detected from the receipt.",
            )
        )

    detected_currency = _pick_currency(summary_fields)
    currency = detected_currency or settings.default_currency
    if detected_currency is None:
        warnings.append(
            ExtractionWarning(
                code="currency_defaulted",
                message=f"Currency was not provided by the provider, defaulted to {settings.default_currency}.",
            )
        )

    confidence_score = _compute_confidence(
        amount_confidence=amount_confidence,
        date_confidence=date_confidence,
        merchant_confidence=merchant_confidence,
        warning_count=len(warnings),
    )

    return NormalizedExtractionResult(
        receipt_id=receipt_id,
        total_amount=total_amount,
        receipt_date=receipt_date,
        merchant_name=merchant_name,
        currency=currency,
        confidence_score=confidence_score,
        warnings=warnings,
        raw_payload_json=payload,
        provider_name=settings.textract_provider_name,
        provider_version=settings.textract_provider_version,
        processed_at=datetime.now(UTC),
    )


def _extract_summary_fields(payload: dict[str, Any]) -> list[dict[str, Any]]:
    expense_documents = payload.get("ExpenseDocuments", [])
    if not expense_documents:
        return []

    first_document = expense_documents[0] or {}
    summary_fields = first_document.get("SummaryFields", [])
    return [field for field in summary_fields if isinstance(field, dict)]


def _pick_total_amount(
    summary_fields: list[dict[str, Any]]
) -> tuple[Decimal | None, Decimal]:
    priority_order = ("TOTAL", "TOTAL_AMOUNT", "SUBTOTAL", "AMOUNT_DUE")
    for field_type in priority_order:
        for field in summary_fields:
            type_text = _field_type(field)
            if type_text != field_type:
                continue
            parsed = _parse_decimal(_value_text(field))
            if parsed is not None:
                return parsed, _confidence(field)

    return None, Decimal("0")


def _pick_receipt_date(
    summary_fields: list[dict[str, Any]]
) -> tuple[date | None, Decimal]:
    priority_order = ("INVOICE_RECEIPT_DATE", "DATE")
    for field_type in priority_order:
        for field in summary_fields:
            if _field_type(field) != field_type:
                continue
            text = _value_text(field)
            if text is None:
                continue
            for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%d %b %Y", "%d %B %Y"):
                try:
                    return datetime.strptime(text, fmt).date(), _confidence(field)
                except ValueError:
                    continue

    return None, Decimal("0")


def _pick_merchant_name(
    summary_fields: list[dict[str, Any]]
) -> tuple[str | None, Decimal]:
    priority_order = ("VENDOR_NAME", "RECEIVER_NAME")
    for field_type in priority_order:
        for field in summary_fields:
            if _field_type(field) != field_type:
                continue
            text = _value_text(field)
            if text:
                return text.strip(), _confidence(field)

    return None, Decimal("0")


def _pick_currency(summary_fields: list[dict[str, Any]]) -> str | None:
    for field in summary_fields:
        currency = field.get("Currency", {})
        code = currency.get("Code")
        if isinstance(code, str) and code.strip():
            return code.strip().upper()
    return None


def _compute_confidence(
    *,
    amount_confidence: Decimal,
    date_confidence: Decimal,
    merchant_confidence: Decimal,
    warning_count: int,
) -> Decimal:
    base_confidence = (
        amount_confidence * Decimal("0.5")
        + date_confidence * Decimal("0.25")
        + merchant_confidence * Decimal("0.25")
    )
    penalty = Decimal("0.1") * Decimal(warning_count)
    result = base_confidence - penalty
    if result < Decimal("0"):
        return Decimal("0")
    if result > Decimal("1"):
        return Decimal("1")
    return result.quantize(Decimal("0.0001"))


def _field_type(field: dict[str, Any]) -> str | None:
    label_detection = field.get("Type", {})
    if not isinstance(label_detection, dict):
        return None
    text = label_detection.get("Text")
    return text.strip().upper() if isinstance(text, str) and text.strip() else None


def _value_text(field: dict[str, Any]) -> str | None:
    value_detection = field.get("ValueDetection", {})
    if not isinstance(value_detection, dict):
        return None
    text = value_detection.get("Text")
    return text.strip() if isinstance(text, str) and text.strip() else None


def _confidence(field: dict[str, Any]) -> Decimal:
    value_detection = field.get("ValueDetection", {})
    raw_confidence = value_detection.get("Confidence")
    if raw_confidence is None:
        return Decimal("0")
    try:
        confidence = Decimal(str(raw_confidence)) / Decimal("100")
    except Exception:
        return Decimal("0")
    if confidence < 0:
        return Decimal("0")
    if confidence > 1:
        return Decimal("1")
    return confidence.quantize(Decimal("0.0001"))


def _parse_decimal(value: str | None) -> Decimal | None:
    if value is None:
        return None

    normalized = (
        value.replace("RM", "")
        .replace(",", "")
        .replace("MYR", "")
        .replace(" ", "")
        .strip()
    )
    try:
        return Decimal(normalized).quantize(Decimal("0.01"))
    except Exception:
        return None
