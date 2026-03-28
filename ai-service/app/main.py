from __future__ import annotations

from datetime import UTC, datetime

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.clients.backend import BackendClient
from app.clients.storage import S3StorageClient
from app.clients.textract import TextractExpenseClient
from app.core.logging import configure_logging
from app.core.settings import Settings, get_settings
from app.models.extraction import DemoAiSummary
from app.services.processing import ReceiptProcessingService

configure_logging()


def build_processing_service(settings: Settings | None = None) -> ReceiptProcessingService:
    resolved_settings = settings or get_settings()
    return ReceiptProcessingService(
        settings=resolved_settings,
        backend_client=BackendClient(resolved_settings),
        storage_client=S3StorageClient(resolved_settings),
        extraction_client=TextractExpenseClient(resolved_settings),
    )


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
        "Queued receipt uploads move through extraction and wait for human review "
        "before contributing to tax relief totals."
    ),
    generated_at=datetime(2026, 3, 27, 0, 0, tzinfo=UTC),
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-service"}


@app.get("/api/demo-summary", response_model=DemoAiSummary)
def get_demo_summary() -> DemoAiSummary:
    return DEMO_AI_SUMMARY
