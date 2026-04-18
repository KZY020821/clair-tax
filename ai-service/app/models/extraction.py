from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ExtractionWarning(BaseModel):
    code: str
    message: str


class NormalizedExtractionResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    receipt_id: str
    total_amount: Decimal | None = None
    receipt_date: date | None = None
    merchant_name: str | None = None
    currency: str | None = None
    confidence_score: Decimal = Field(default=Decimal("0.0"))
    warnings: list[ExtractionWarning] = Field(default_factory=list)
    raw_payload_json: dict[str, Any]
    provider_name: str
    provider_version: str
    processed_at: datetime
    error_code: str | None = None
    error_message: str | None = None


class DemoAiSummary(BaseModel):
    service: str
    status: str
    detected_receipt_count: int
    extracted_total_amount: float
    suggestion_preview: str
    generated_at: datetime
