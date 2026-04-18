from __future__ import annotations

from dataclasses import dataclass
import json
import logging
from typing import Any

from app.core.settings import get_settings
from app.models.jobs import ReceiptProcessingJob
from app.services.processing import (
    NonRetriableProcessingError,
    ReceiptProcessingService,
    RetriableProcessingError,
)

LOGGER = logging.getLogger(__name__)


@dataclass
class LambdaBatchProcessor:
    service: ReceiptProcessingService

    def handle(self, event: dict[str, Any]) -> dict[str, list[dict[str, str]]]:
        batch_failures: list[dict[str, str]] = []

        for record in event.get("Records", []):
            message_id = str(record.get("messageId", "unknown-message"))
            try:
                body = record.get("body", "{}")
                payload = json.loads(body)
                job = ReceiptProcessingJob.model_validate(payload)
                self.service.process_job(job)
            except NonRetriableProcessingError as exc:
                try:
                    payload = json.loads(record.get("body", "{}"))
                    job = ReceiptProcessingJob.model_validate(payload)
                    self.service.report_terminal_failure(
                        job,
                        error_code=exc.error_code,
                        error_message=str(exc),
                    )
                except Exception:
                    LOGGER.exception(
                        "Unable to report terminal receipt-processing failure for message_id=%s",
                        message_id,
                    )
            except RetriableProcessingError:
                batch_failures.append({"itemIdentifier": message_id})
            except Exception:
                LOGGER.exception(
                    "Unexpected receipt-processing failure for message_id=%s", message_id
                )
                batch_failures.append({"itemIdentifier": message_id})

        return {"batchItemFailures": batch_failures}


def lambda_handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    from app.main import build_processing_service

    processor = LambdaBatchProcessor(service=build_processing_service(get_settings()))
    return processor.handle(event)
