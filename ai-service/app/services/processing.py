import asyncio
import time

import structlog

from app.clients.backend_api import BackendApiClient
from app.clients.ocr import OcrExtractionError, extract_blocks_from_file
from app.clients.s3_storage import S3StorageClient
from app.config import get_settings
from app.models.extraction import ExtractionResult
from app.models.job import ReceiptJob
from app.services.normalization import (
    extract_amount_candidates,
    extract_date_candidates,
    extract_einvoice_metadata,
    extract_merchant_candidates,
)
from app.services.postprocessing import postprocess

logger = structlog.get_logger(__name__)


async def process_receipt(job: ReceiptJob) -> ExtractionResult:
    """
    Full receipt processing pipeline:
    1. Download from S3
    2. Run OCR in executor (non-blocking)
    3. Normalise
    4. Postprocess
    5. Write result to Spring Boot
    6. Return ExtractionResult
    """
    settings = get_settings()
    bound_logger = logger.bind(receipt_id=job.receipt_id, s3_key=job.s3_key)
    bound_logger.info("receipt_processing_started", receipt_id=job.receipt_id, s3_key=job.s3_key)

    loop = asyncio.get_event_loop()

    # --- 1. Download from S3 ---
    s3_client = S3StorageClient(settings)
    dl_start = time.monotonic()
    image_bytes = await loop.run_in_executor(None, s3_client.download_receipt, job.s3_key)
    dl_latency = int((time.monotonic() - dl_start) * 1000)
    bound_logger.debug(
        "s3_download_complete",
        receipt_id=job.receipt_id,
        size_bytes=len(image_bytes),
        latency_ms=dl_latency,
    )

    # --- 2. OCR ---
    ocr_start = time.monotonic()
    try:
        blocks = await loop.run_in_executor(None, extract_blocks_from_file, image_bytes, job.receipt_id)
    except OcrExtractionError as exc:
        bound_logger.error(
            "receipt_processing_failed",
            receipt_id=job.receipt_id,
            error_type="ocr_error",
            detail=str(exc.cause),
        )
        result = ExtractionResult(
            receipt_id=job.receipt_id,
            extraction_status="failed",
            raw_ocr_block_count=0,
            error_detail="ocr_error",
        )
        await _write_result(result, bound_logger)
        return result

    ocr_latency = int((time.monotonic() - ocr_start) * 1000)
    bound_logger.debug(
        "ocr_complete",
        receipt_id=job.receipt_id,
        block_count=len(blocks),
        latency_ms=ocr_latency,
    )

    # --- Handle zero blocks ---
    if not blocks:
        result = ExtractionResult(
            receipt_id=job.receipt_id,
            extraction_status="no_text_detected",
            raw_ocr_block_count=0,
        )
        bound_logger.info(
            "extraction_complete",
            receipt_id=job.receipt_id,
            status=result.extraction_status,
            amount=None,
            date=None,
            merchant=None,
        )
        await _write_result(result, bound_logger)
        return result

    # --- 3. Normalise ---
    amount_candidates = extract_amount_candidates(blocks)
    date_candidates = extract_date_candidates(blocks)
    merchant_candidates = extract_merchant_candidates(blocks)
    einvoice_meta = extract_einvoice_metadata(blocks)

    # --- 4. Postprocess ---
    result = postprocess(
        blocks=blocks,
        amount_candidates=amount_candidates,
        date_candidates=date_candidates,
        merchant_candidates=merchant_candidates,
        job=job,
        settings=settings,
    )

    # --- 4a. Merge e-invoice metadata into result ---
    if einvoice_meta.get("is_einvoice"):
        result = result.model_copy(update={
            "is_einvoice": True,
            "einvoice_uuid": einvoice_meta.get("einvoice_uuid"),
            "einvoice_number": einvoice_meta.get("einvoice_number"),
            "supplier_tin": einvoice_meta.get("supplier_tin"),
        })

    bound_logger.info(
        "extraction_complete",
        receipt_id=job.receipt_id,
        status=result.extraction_status,
        amount=result.amount,
        date=result.date,
        merchant=result.merchant_name,
    )

    # --- 5. Write to backend ---
    await _write_result(result, bound_logger)

    return result


async def _write_result(result: ExtractionResult, bound_logger) -> None:
    settings = get_settings()
    backend_client = BackendApiClient(settings)
    write_start = time.monotonic()
    await backend_client.write_extraction_result(result.receipt_id, result)
    write_latency = int((time.monotonic() - write_start) * 1000)
    bound_logger.info(
        "backend_write_complete",
        receipt_id=result.receipt_id,
        latency_ms=write_latency,
    )
