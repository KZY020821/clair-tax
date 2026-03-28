from __future__ import annotations

from dataclasses import dataclass
import logging

from app.clients.backend import BackendClient, BackendClientError
from app.clients.storage import S3StorageClient, StorageClientError
from app.clients.textract import ExtractionProviderError, TextractExpenseClient
from app.core.settings import Settings
from app.models.extraction import NormalizedExtractionResult
from app.models.jobs import ReceiptProcessingJob
from app.services.postprocessing import (
    ModelArtifactError,
    postprocess_textract_expense_payload,
)

LOGGER = logging.getLogger(__name__)


class RetriableProcessingError(RuntimeError):
    pass


class NonRetriableProcessingError(RuntimeError):
    def __init__(self, message: str, *, error_code: str) -> None:
        super().__init__(message)
        self.error_code = error_code


@dataclass
class ReceiptProcessingService:
    settings: Settings
    backend_client: BackendClient
    storage_client: S3StorageClient
    extraction_client: TextractExpenseClient

    def process_job(self, job: ReceiptProcessingJob) -> NormalizedExtractionResult:
        LOGGER.info(
            "Processing receipt job job_id=%s receipt_id=%s correlation_id=%s",
            job.job_id,
            job.receipt_id,
            job.correlation_id,
        )

        self.backend_client.record_processing_attempt(
            job.receipt_id, job.job_id, "processing"
        )

        try:
            document_bytes = self.storage_client.get_object_bytes(job.s3_bucket, job.s3_key)
            provider_payload = self.extraction_client.analyze_expense(
                document_bytes, job.mime_type
            )
            result = postprocess_textract_expense_payload(
                receipt_id=job.receipt_id,
                payload=provider_payload,
                mime_type=job.mime_type,
                settings=self.settings,
            )
            self.backend_client.submit_extraction_result(job.receipt_id, job.job_id, result)
            return result
        except BackendClientError as exc:
            if exc.status_code is not None and exc.status_code >= 500:
                raise RetriableProcessingError(str(exc)) from exc
            raise NonRetriableProcessingError(
                str(exc), error_code="backend_writeback_rejected"
            ) from exc
        except ModelArtifactError as exc:
            raise NonRetriableProcessingError(
                str(exc), error_code="receipt_model_artifact_error"
            ) from exc
        except (StorageClientError, ExtractionProviderError) as exc:
            raise RetriableProcessingError(str(exc)) from exc

    def report_terminal_failure(
        self, job: ReceiptProcessingJob, *, error_code: str, error_message: str
    ) -> None:
        LOGGER.error(
            "Marking receipt job as failed job_id=%s receipt_id=%s error_code=%s",
            job.job_id,
            job.receipt_id,
            error_code,
        )
        self.backend_client.record_processing_attempt(
            job.receipt_id,
            job.job_id,
            "failed",
            error_code=error_code,
            error_message=error_message,
        )
