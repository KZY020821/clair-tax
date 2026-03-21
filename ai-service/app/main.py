from datetime import datetime, timezone
from typing import Literal

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel


class DemoAiSummary(BaseModel):
    service: str
    status: Literal["ready"]
    detected_receipt_count: int
    extracted_total_amount: float
    suggestion_preview: str
    generated_at: datetime


app = FastAPI(title="Clair Tax AI Service")

app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$",
    allow_methods=["GET"],
    allow_headers=["*"],
)

DEMO_AI_SUMMARY = DemoAiSummary(
    service="ai-service",
    status="ready",
    detected_receipt_count=3,
    extracted_total_amount=1820.50,
    suggestion_preview=(
        "Lifestyle receipts were grouped successfully and look ready for user review "
        "before tax filing."
    ),
    generated_at=datetime(2026, 3, 20, 0, 0, tzinfo=timezone.utc),
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-service"}


@app.get("/api/demo-summary", response_model=DemoAiSummary)
def get_demo_summary() -> DemoAiSummary:
    return DEMO_AI_SUMMARY
