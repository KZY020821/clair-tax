import json

from app.handlers.sqs import LambdaBatchProcessor
from app.services.processing import (
    NonRetriableProcessingError,
    RetriableProcessingError,
)


class StubProcessingService:
    def __init__(self, mode: str = "success") -> None:
        self.mode = mode
        self.reported_failures: list[tuple[str, str]] = []

    def process_job(self, job):
        if self.mode == "retriable":
            raise RetriableProcessingError("retry me")
        if self.mode == "non_retriable":
            raise NonRetriableProcessingError(
                "do not retry me", error_code="invalid_receipt"
            )
        return None

    def report_terminal_failure(
        self, job, *, error_code: str, error_message: str
    ) -> None:
        self.reported_failures.append((error_code, error_message))


def build_event() -> dict:
    return {
        "Records": [
            {
                "messageId": "message-1",
                "body": json.dumps(
                    {
                        "schema_version": "2026-03-27",
                        "job_id": "job-123",
                        "receipt_id": "receipt-123",
                        "user_id": "user-123",
                        "policy_year": 2025,
                        "relief_category_id": "category-123",
                        "s3_bucket": "receipt-bucket",
                        "s3_key": "receipts/demo.pdf",
                        "mime_type": "application/pdf",
                        "file_size_bytes": 1024,
                        "sha256_hash": "abc123",
                        "uploaded_at": "2026-03-27T00:00:00Z",
                        "correlation_id": "corr-123",
                    }
                ),
            }
        ]
    }


def test_lambda_batch_processor_returns_retry_failures() -> None:
    processor = LambdaBatchProcessor(service=StubProcessingService(mode="retriable"))

    result = processor.handle(build_event())

    assert result == {"batchItemFailures": [{"itemIdentifier": "message-1"}]}


def test_lambda_batch_processor_reports_non_retriable_failures() -> None:
    service = StubProcessingService(mode="non_retriable")
    processor = LambdaBatchProcessor(service=service)

    result = processor.handle(build_event())

    assert result == {"batchItemFailures": []}
    assert service.reported_failures == [("invalid_receipt", "do not retry me")]
