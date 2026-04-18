from __future__ import annotations

from datetime import date
from decimal import Decimal

import pytest

from app.core.settings import Settings
from app.services.postprocessing import (
    ReceiptModelArtifact,
    extract_candidate_bundle,
    postprocess_textract_expense_payload,
)


class AmountPriorityModel:
    def predict_proba(self, rows):
        probabilities = []
        for row in rows:
            positive = min(0.99, 0.10 + row[0] * 0.55 + row[1] * 0.20 - row[2] * 0.15)
            probabilities.append([1.0 - positive, positive])
        return probabilities


class DatePriorityModel:
    def predict_proba(self, rows):
        probabilities = []
        for row in rows:
            positive = min(0.99, 0.10 + row[0] * 0.55 + row[1] * 0.20)
            probabilities.append([1.0 - positive, positive])
        return probabilities


class ConstantProbabilityModel:
    def __init__(self, probability: float) -> None:
        self.probability = probability

    def predict_proba(self, rows):
        return [[1.0 - self.probability, self.probability] for _ in rows]


def build_payload() -> dict[str, object]:
    return {
        "ExpenseDocuments": [
            {
                "SummaryFields": [
                    {
                        "Type": {"Text": "VENDOR_NAME"},
                        "ValueDetection": {"Text": "Bookshop Sdn Bhd", "Confidence": 98.5},
                    },
                    {
                        "Type": {"Text": "INVOICE_RECEIPT_DATE"},
                        "ValueDetection": {"Text": "2025-05-12", "Confidence": 93.0},
                    },
                    {
                        "Type": {"Text": "DATE"},
                        "ValueDetection": {"Text": "2025-05-14", "Confidence": 80.0},
                    },
                    {
                        "Type": {"Text": "SUBTOTAL"},
                        "ValueDetection": {"Text": "RM 80.00", "Confidence": 90.0},
                        "Currency": {"Code": "MYR"},
                    },
                    {
                        "Type": {"Text": "TOTAL"},
                        "ValueDetection": {"Text": "RM 89.90", "Confidence": 97.0},
                        "Currency": {"Code": "MYR"},
                    },
                ]
            }
        ]
    }


def test_extract_candidate_bundle_collects_amount_and_date_candidates() -> None:
    bundle = extract_candidate_bundle(build_payload())

    assert bundle.merchant_name == "Bookshop Sdn Bhd"
    assert bundle.currency_code == "MYR"
    assert [candidate.normalized_value for candidate in bundle.amount_candidates] == [
        Decimal("80.00"),
        Decimal("89.90"),
    ]
    assert [candidate.parsed_date.isoformat() for candidate in bundle.date_candidates] == [
        "2025-05-12",
        "2025-05-14",
    ]


def test_postprocess_uses_trained_models_when_feature_flag_is_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    artifact = ReceiptModelArtifact(
        schema_version="2026-03-27",
        artifact_version="artifact-123",
        trained_at="2026-03-27T00:00:00Z",
        amount_candidate_model=AmountPriorityModel(),
        date_candidate_model=DatePriorityModel(),
        receipt_validity_model=ConstantProbabilityModel(0.92),
        thresholds={
            "amount_selection_threshold": 0.60,
            "date_selection_threshold": 0.60,
            "validity_threshold": 0.55,
        },
        metrics={},
    )
    monkeypatch.setattr(
        "app.services.postprocessing.load_receipt_model_artifact",
        lambda _: artifact,
    )

    result = postprocess_textract_expense_payload(
        receipt_id="receipt-123",
        payload=build_payload(),
        mime_type="application/pdf",
        settings=Settings(
            trained_postprocessor_enabled=True,
            trained_postprocessor_artifact_path="/tmp/receipt-model.joblib",
        ),
    )

    assert result.total_amount == Decimal("89.90")
    assert result.receipt_date == date(2025, 5, 12)
    assert result.error_code is None
    assert "receipt-postprocessor:artifact-123" in result.provider_version
    assert result.raw_payload_json["postprocessor"]["artifact_version"] == "artifact-123"


def test_postprocess_returns_invalid_reason_for_unreadable_receipts(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    artifact = ReceiptModelArtifact(
        schema_version="2026-03-27",
        artifact_version="artifact-456",
        trained_at="2026-03-27T00:00:00Z",
        amount_candidate_model=ConstantProbabilityModel(0.20),
        date_candidate_model=ConstantProbabilityModel(0.20),
        receipt_validity_model=ConstantProbabilityModel(0.10),
        thresholds={
            "amount_selection_threshold": 0.60,
            "date_selection_threshold": 0.60,
            "validity_threshold": 0.55,
        },
        metrics={},
    )
    monkeypatch.setattr(
        "app.services.postprocessing.load_receipt_model_artifact",
        lambda _: artifact,
    )

    result = postprocess_textract_expense_payload(
        receipt_id="receipt-456",
        payload={"ExpenseDocuments": [{"SummaryFields": []}]},
        mime_type="application/pdf",
        settings=Settings(
            trained_postprocessor_enabled=True,
            trained_postprocessor_artifact_path="/tmp/receipt-model.joblib",
        ),
    )

    assert result.total_amount is None
    assert result.receipt_date is None
    assert result.error_code == "unreadable_receipt"
    assert "readable receipt text" in result.error_message
