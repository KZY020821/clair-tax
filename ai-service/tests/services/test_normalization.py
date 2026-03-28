from decimal import Decimal

from app.core.settings import Settings
from app.services.normalization import normalize_textract_expense_payload


def test_normalize_textract_expense_payload_extracts_candidate_fields() -> None:
    payload = {
        "ExpenseDocuments": [
            {
                "SummaryFields": [
                    {
                        "Type": {"Text": "VENDOR_NAME"},
                        "ValueDetection": {"Text": "Clinic Sentosa", "Confidence": 98.0},
                    },
                    {
                        "Type": {"Text": "INVOICE_RECEIPT_DATE"},
                        "ValueDetection": {"Text": "2025-06-12", "Confidence": 95.0},
                    },
                    {
                        "Type": {"Text": "TOTAL"},
                        "ValueDetection": {"Text": "RM 120.50", "Confidence": 97.0},
                        "Currency": {"Code": "MYR"},
                    },
                ]
            }
        ]
    }

    result = normalize_textract_expense_payload(
        receipt_id="receipt-123",
        payload=payload,
        settings=Settings(),
    )

    assert result.receipt_id == "receipt-123"
    assert result.merchant_name == "Clinic Sentosa"
    assert result.receipt_date.isoformat() == "2025-06-12"
    assert result.total_amount == Decimal("120.50")
    assert result.currency == "MYR"
    assert result.confidence_score > Decimal("0.90")
    assert result.warnings == []


def test_normalize_textract_expense_payload_adds_warnings_when_fields_missing() -> None:
    payload = {"ExpenseDocuments": [{"SummaryFields": []}]}

    result = normalize_textract_expense_payload(
        receipt_id="receipt-456",
        payload=payload,
        settings=Settings(),
    )

    assert result.total_amount is None
    assert result.receipt_date is None
    assert result.merchant_name is None
    assert result.currency == "MYR"
    assert [warning.code for warning in result.warnings] == [
        "total_missing",
        "date_missing",
        "merchant_missing",
        "currency_defaulted",
    ]
