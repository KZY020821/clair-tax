from typing import Literal

from pydantic import BaseModel


class ExtractionResult(BaseModel):
    receipt_id: str
    extraction_status: Literal["extracted", "partial", "invalid", "no_text_detected", "failed"]
    amount: str | None = None
    currency: str | None = None
    date: str | None = None
    merchant_name: str | None = None
    amount_confidence: float | None = None
    date_confidence: float | None = None
    merchant_confidence: float | None = None
    raw_ocr_block_count: int = 0
    processing_mode: Literal["heuristic", "model"] = "heuristic"
    error_detail: str | None = None
    # MyInvois e-invoice metadata (populated when a validated e-invoice is uploaded)
    is_einvoice: bool = False
    einvoice_uuid: str | None = None
    einvoice_number: str | None = None
    supplier_tin: str | None = None
