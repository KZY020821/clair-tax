from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.models.training import ReceiptDatasetManifestEntry
from app.services.training import (
    build_training_dataset,
    load_manifest_entries,
    scaffold_annotation_manifest,
    split_manifest_entries,
    train_receipt_models,
    write_training_tables,
)


def build_training_entry(
    *,
    receipt_id: str,
    amount: str,
    receipt_date: str,
    is_valid_receipt: bool = True,
    invalid_reason: str | None = None,
) -> ReceiptDatasetManifestEntry:
    payload = {
        "ExpenseDocuments": [
            {
                "SummaryFields": [
                    {
                        "Type": {"Text": "VENDOR_NAME"},
                        "ValueDetection": {"Text": f"Merchant {receipt_id}", "Confidence": 98.0},
                    },
                    {
                        "Type": {"Text": "INVOICE_RECEIPT_DATE"},
                        "ValueDetection": {"Text": receipt_date, "Confidence": 96.0},
                    },
                    {
                        "Type": {"Text": "DATE"},
                        "ValueDetection": {"Text": "2025-01-01", "Confidence": 80.0},
                    },
                    {
                        "Type": {"Text": "SUBTOTAL"},
                        "ValueDetection": {"Text": "RM 50.00", "Confidence": 88.0},
                        "Currency": {"Code": "MYR"},
                    },
                    {
                        "Type": {"Text": "TOTAL"},
                        "ValueDetection": {"Text": f"RM {amount}", "Confidence": 97.0},
                        "Currency": {"Code": "MYR"},
                    },
                ]
            }
        ]
    }
    return ReceiptDatasetManifestEntry(
        receipt_id=receipt_id,
        file_path=f"/tmp/{receipt_id}.pdf",
        mime_type="application/pdf",
        raw_payload_json=payload,
        is_valid_receipt=is_valid_receipt,
        invalid_reason=invalid_reason,
        correct_total_amount=None if not is_valid_receipt else amount,
        correct_receipt_date=None if not is_valid_receipt else receipt_date,
        annotation_source="test",
    )


def test_load_manifest_entries_rejects_missing_labels(tmp_path: Path) -> None:
    manifest_path = tmp_path / "manifest.jsonl"
    manifest_path.write_text(
        json.dumps(
            {
                "receipt_id": "receipt-1",
                "file_path": "/tmp/receipt-1.pdf",
                "mime_type": "application/pdf",
                "raw_payload_json": {"ExpenseDocuments": []},
            }
        )
        + "\n",
        encoding="utf-8",
    )

    with pytest.raises(ValueError, match="is_valid_receipt"):
        load_manifest_entries(manifest_path)


def test_split_manifest_entries_keeps_receipt_ids_disjoint() -> None:
    entries = [
        build_training_entry(
            receipt_id=f"receipt-{index}",
            amount=f"{80 + index}.90",
            receipt_date=f"2025-05-{index + 10:02d}",
        )
        for index in range(6)
    ]

    train_entries, holdout_entries = split_manifest_entries(entries, seed=7)

    assert {entry.receipt_id for entry in train_entries}.isdisjoint(
        {entry.receipt_id for entry in holdout_entries}
    )


def test_scaffold_annotation_manifest_writes_template_rows(tmp_path: Path) -> None:
    sample_dir = tmp_path / "samples"
    sample_dir.mkdir()
    (sample_dir / "receipt-a.pdf").write_bytes(b"pdf")
    (sample_dir / "receipt-b.jpg").write_bytes(b"jpg")

    output_path = tmp_path / "annotations.jsonl"
    receipt_count = scaffold_annotation_manifest(sample_dir, output_path)

    content = output_path.read_text(encoding="utf-8").strip().splitlines()
    assert receipt_count == 2
    assert len(content) == 2


def test_train_receipt_models_writes_artifact_and_candidate_tables(
    tmp_path: Path,
) -> None:
    entries = [
        build_training_entry(
            receipt_id=f"valid-{index}",
            amount=f"{90 + index}.90",
            receipt_date=f"2025-05-{index + 10:02d}",
        )
        for index in range(8)
    ] + [
        build_training_entry(
            receipt_id="invalid-1",
            amount="0.00",
            receipt_date="2025-05-25",
            is_valid_receipt=False,
            invalid_reason="not_a_receipt",
        ),
        build_training_entry(
            receipt_id="invalid-2",
            amount="0.00",
            receipt_date="2025-05-26",
            is_valid_receipt=False,
            invalid_reason="unreadable_receipt",
        ),
    ]

    artifact_path = tmp_path / "receipt_postprocessor.joblib"
    result = train_receipt_models(
        entries,
        output_path=artifact_path,
        holdout_ratio=0.1,
        seed=3,
    )
    dataset = build_training_dataset(entries)
    table_paths = write_training_tables(dataset, tmp_path / "tables")

    assert artifact_path.exists()
    assert result["artifact_version"]
    assert "metrics" in result
    assert Path(table_paths["amount_candidates"]).exists()
    assert Path(table_paths["date_candidates"]).exists()
    assert Path(table_paths["receipt_validity"]).exists()
