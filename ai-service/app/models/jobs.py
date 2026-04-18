from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ReceiptProcessingJob(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schema_version: str
    job_id: str
    receipt_id: str
    user_id: str
    policy_year: int
    relief_category_id: str | None = None
    s3_bucket: str
    s3_key: str
    mime_type: str
    file_size_bytes: int = Field(ge=0)
    sha256_hash: str
    uploaded_at: datetime
    correlation_id: str
