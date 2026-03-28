from datetime import UTC, datetime
from decimal import Decimal

from app.core.settings import Settings
from app.models.jobs import ReceiptProcessingJob
from app.services.processing import (
    ReceiptProcessingService,
)


class FakeBackendClient:
    def __init__(self) -> None:
        self.processing_attempts: list[tuple[str, str, str]] = []
        self.submitted_receipt_ids: list[str] = []

    def record_processing_attempt(
        self,
        receipt_id: str,
        job_id: str,
        status: str,
        *,
        error_code: str | None = None,
        error_message: str | None = None,
    ) -> None:
        self.processing_attempts.append((receipt_id, job_id, status))

    def submit_extraction_result(self, receipt_id: str, job_id: str, result) -> None:
        self.submitted_receipt_ids.append(receipt_id)


class FakeStorageClient:
    def get_object_bytes(self, bucket: str, key: str) -> bytes:
        assert bucket == "receipt-bucket"
        assert key == "receipts/demo.pdf"
        return b"pdf-bytes"


class FakeExtractionClient:
    def analyze_expense(self, document_bytes: bytes, mime_type: str):
        assert document_bytes == b"pdf-bytes"
        assert mime_type == "application/pdf"
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
                            "ValueDetection": {"Text": "2025-05-12", "Confidence": 92.0},
                        },
                        {
                            "Type": {"Text": "TOTAL"},
                            "ValueDetection": {"Text": "89.90", "Confidence": 96.0},
                            "Currency": {"Code": "MYR"},
                        },
                    ]
                }
            ]
        }


def build_job() -> ReceiptProcessingJob:
    return ReceiptProcessingJob(
        schema_version="2026-03-27",
        job_id="job-123",
        receipt_id="receipt-123",
        user_id="user-123",
        policy_year=2025,
        relief_category_id="category-123",
        s3_bucket="receipt-bucket",
        s3_key="receipts/demo.pdf",
        mime_type="application/pdf",
        file_size_bytes=1024,
        sha256_hash="abc123",
        uploaded_at=datetime(2026, 3, 27, 0, 0, tzinfo=UTC),
        correlation_id="corr-123",
    )


def test_process_job_happy_path() -> None:
    backend_client = FakeBackendClient()
    service = ReceiptProcessingService(
        settings=Settings(),
        backend_client=backend_client,
        storage_client=FakeStorageClient(),
        extraction_client=FakeExtractionClient(),
    )

    result = service.process_job(build_job())

    assert result.total_amount == Decimal("89.90")
    assert result.merchant_name == "Bookshop Sdn Bhd"
    assert result.currency == "MYR"
    assert backend_client.processing_attempts == [("receipt-123", "job-123", "processing")]
    assert backend_client.submitted_receipt_ids == ["receipt-123"]
