from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Any

from pydantic import BaseModel, ConfigDict, model_validator


class ReceiptDatasetManifestEntry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    receipt_id: str
    mime_type: str
    file_path: str | None = None
    s3_key: str | None = None
    ocr_payload_path: str | None = None
    raw_payload_json: dict[str, Any] | None = None
    is_valid_receipt: bool | None = None
    invalid_reason: str | None = None
    invalid_reason_message: str | None = None
    correct_total_amount: Decimal | None = None
    correct_receipt_date: date | None = None
    merchant_name: str | None = None
    currency: str | None = None
    language: str | None = None
    ocr_quality: str | None = None
    annotation_source: str | None = None
    provider_name: str | None = None
    provider_version: str | None = None
    reviewed_at: datetime | None = None

    @model_validator(mode="after")
    def validate_labels(self) -> "ReceiptDatasetManifestEntry":
        if self.file_path is None and self.s3_key is None:
            raise ValueError("Each manifest row requires either file_path or s3_key")

        if self.is_valid_receipt is True and self.invalid_reason is not None:
            raise ValueError("Valid receipt rows cannot include invalid_reason")

        if (
            self.is_valid_receipt is False
            and (
                self.correct_total_amount is not None
                or self.correct_receipt_date is not None
            )
        ):
            raise ValueError(
                "Invalid receipt rows cannot include corrected amount or date labels"
            )

        return self


class ReviewedReceiptTrainingExample(BaseModel):
    model_config = ConfigDict(extra="forbid")

    receipt_id: str
    policy_year: int
    file_name: str
    file_url: str
    s3_bucket: str
    s3_key: str
    mime_type: str
    file_size_bytes: int
    is_valid_receipt: bool
    invalid_reason: str | None = None
    invalid_reason_message: str | None = None
    correct_total_amount: Decimal | None = None
    correct_receipt_date: date | None = None
    merchant_name: str | None = None
    currency: str | None = None
    raw_payload_json: dict[str, Any]
    provider_name: str | None = None
    provider_version: str | None = None
    annotation_source: str
    reviewed_at: datetime
