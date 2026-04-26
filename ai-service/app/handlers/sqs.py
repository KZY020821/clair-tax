import asyncio
import json
import traceback
from typing import Any

import structlog
from pydantic import ValidationError

from app.clients.backend_api import BackendApiError
from app.clients.s3_storage import StorageError
from app.models.job import ReceiptJob
from app.services.processing import process_receipt

logger = structlog.get_logger(__name__)


def lambda_handler(event: dict, context: Any) -> dict:
    """
    AWS Lambda entry point triggered by SQS.

    Implements partial batch failure: failed records are reported in
    batchItemFailures so SQS can retry them while successful records
    are not reprocessed.
    """
    records = event.get("Records", [])
    batch_item_failures: list[dict] = []

    for record in records:
        message_id = record.get("messageId", "unknown")
        bound_logger = logger.bind(message_id=message_id)

        try:
            body = record.get("body", "")
            payload = json.loads(body)
        except (json.JSONDecodeError, TypeError) as exc:
            bound_logger.error(
                "sqs_record_invalid_json",
                message_id=message_id,
                detail=str(exc),
            )
            batch_item_failures.append({"itemIdentifier": message_id})
            continue

        try:
            job = ReceiptJob(**payload)
        except (ValidationError, TypeError) as exc:
            bound_logger.error(
                "sqs_record_invalid_schema",
                message_id=message_id,
                detail=str(exc),
            )
            batch_item_failures.append({"itemIdentifier": message_id})
            continue

        bound_logger = bound_logger.bind(receipt_id=job.receipt_id, s3_key=job.s3_key)

        try:
            asyncio.run(process_receipt(job))
            bound_logger.info("sqs_record_processed_successfully", receipt_id=job.receipt_id)

        except StorageError as exc:
            bound_logger.error(
                "sqs_record_storage_error",
                receipt_id=job.receipt_id,
                detail=str(exc),
                not_found=exc.not_found,
            )
            batch_item_failures.append({"itemIdentifier": message_id})

        except BackendApiError as exc:
            # process_receipt already handles 4xx (logs, doesn't raise).
            # BackendApiError is only raised for 5xx / timeout / connection refused.
            bound_logger.error(
                "sqs_record_backend_error",
                receipt_id=job.receipt_id,
                detail=str(exc),
            )
            batch_item_failures.append({"itemIdentifier": message_id})

        except Exception as exc:
            bound_logger.error(
                "sqs_record_unexpected_error",
                receipt_id=job.receipt_id,
                detail=str(exc),
                traceback=traceback.format_exc(),
            )
            batch_item_failures.append({"itemIdentifier": message_id})

    return {"batchItemFailures": batch_item_failures}
