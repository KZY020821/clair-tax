import asyncio
import io
import time
import traceback

import structlog
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image

from app.clients.backend_api import BackendApiError
from app.clients.ocr import (
    OcrExtractionError,
    _is_pdf,
    extract_blocks,
    extract_blocks_from_file,
    get_ocr_reader,
)
from app.clients.s3_storage import StorageError
from app.config import get_settings
from app.models.extraction import ExtractionResult
from app.models.job import ReceiptJob
from app.routers.chat import router as chat_router
from app.services.intent_classifier import prewarm_intent_classifier
from app.services.normalization import (
    extract_amount_candidates,
    extract_date_candidates,
    extract_merchant_candidates,
)
from app.services.postprocessing import postprocess
from app.services.processing import process_receipt

logger = structlog.get_logger(__name__)

app = FastAPI(
    title="Clair Tax AI Service",
    description="Receipt OCR microservice for Malaysian personal tax management",
    version="1.0.0",
)

app.include_router(chat_router)


@app.on_event("startup")
async def startup_event() -> None:
    """Pre-warm EasyOCR reader and intent classifier so first requests aren't slow."""
    loop = asyncio.get_event_loop()
    try:
        await loop.run_in_executor(None, get_ocr_reader)
        logger.info("easyocr_prewarm_complete")
    except Exception as exc:
        logger.warning("easyocr_prewarm_failed", detail=str(exc))
    try:
        await loop.run_in_executor(None, prewarm_intent_classifier)
    except Exception as exc:
        logger.warning("intent_classifier_prewarm_failed", detail=str(exc))


@app.get("/health")
async def health() -> dict:
    """Health check — never returns 500."""
    settings = get_settings()
    ocr_ready = False
    model_loaded = False

    try:
        reader = get_ocr_reader()
        ocr_ready = reader is not None
    except Exception:
        pass

    try:
        import os

        model_path = settings.TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH
        model_loaded = os.path.isfile(model_path)
    except Exception:
        pass

    return {
        "status": "ok",
        "ocr_ready": ocr_ready,
        "model_loaded": model_loaded,
        "s3_bucket": settings.S3_BUCKET_NAME,
        "backend_url": settings.BACKEND_API_BASE_URL,
    }


@app.post("/api/process-receipt", response_model=ExtractionResult)
async def process_receipt_endpoint(job: ReceiptJob) -> ExtractionResult:
    try:
        result = await process_receipt(job)
        return result
    except StorageError as exc:
        logger.error(
            "process_receipt_s3_error",
            receipt_id=job.receipt_id,
            detail=str(exc),
            not_found=exc.not_found,
        )
        return ExtractionResult(
            receipt_id=job.receipt_id,
            extraction_status="failed",
            error_detail="s3_error",
        )
    except BackendApiError as exc:
        logger.error(
            "process_receipt_backend_error",
            receipt_id=job.receipt_id,
            detail=str(exc),
        )
        return ExtractionResult(
            receipt_id=job.receipt_id,
            extraction_status="failed",
            error_detail="backend_error",
        )
    except Exception as exc:
        logger.error(
            "process_receipt_unexpected_error",
            receipt_id=job.receipt_id,
            detail=str(exc),
            traceback=traceback.format_exc(),
        )
        raise HTTPException(status_code=500, detail=f"Unexpected error: {exc}")


@app.post("/api/demo-summary", response_model=ExtractionResult)
async def demo_summary(file: UploadFile = File(...)) -> ExtractionResult:
    """
    Accepts a multipart receipt image, runs OCR and extraction without S3.
    Returns ExtractionResult. No backend write.
    """
    settings = get_settings()
    loop = asyncio.get_event_loop()

    raw_bytes = await file.read()

    # Validate the file is a supported type: PNG, JPG, JPEG, or PDF
    if not _is_pdf(raw_bytes):
        try:
            Image.open(io.BytesIO(raw_bytes)).verify()
        except Exception:
            raise HTTPException(
                status_code=422,
                detail="Uploaded file must be a PNG, JPG, JPEG, or PDF.",
            )

    receipt_id = f"demo-{file.filename or 'upload'}"

    try:
        blocks = await loop.run_in_executor(None, extract_blocks_from_file, raw_bytes, receipt_id)
    except OcrExtractionError:
        return ExtractionResult(
            receipt_id=receipt_id,
            extraction_status="failed",
            error_detail="ocr_error",
        )

    if not blocks:
        return ExtractionResult(
            receipt_id=receipt_id,
            extraction_status="no_text_detected",
        )

    amount_candidates = extract_amount_candidates(blocks)
    date_candidates = extract_date_candidates(blocks)
    merchant_candidates = extract_merchant_candidates(blocks)

    fake_job = ReceiptJob(
        receipt_id=receipt_id,
        s3_key="receipts/demo",
        user_id="demo-user",
        currency=settings.DEFAULT_RECEIPT_CURRENCY,
    )

    result = postprocess(
        blocks=blocks,
        amount_candidates=amount_candidates,
        date_candidates=date_candidates,
        merchant_candidates=merchant_candidates,
        job=fake_job,
        settings=settings,
    )
    return result


@app.get("/api/health/ocr")
async def ocr_health() -> dict:
    """Run OCR on a synthetic image to verify EasyOCR is working. Always 200."""
    loop = asyncio.get_event_loop()
    start = time.monotonic()

    try:
        # Create a minimal synthetic receipt image
        img = Image.new("RGB", (400, 100), color=(255, 255, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        blocks = await loop.run_in_executor(None, extract_blocks, image_bytes, "health-check")
        latency_ms = int((time.monotonic() - start) * 1000)
        return {
            "status": "ok",
            "latency_ms": latency_ms,
            "blocks_detected": len(blocks),
        }
    except Exception as exc:
        latency_ms = int((time.monotonic() - start) * 1000)
        return {
            "status": "error",
            "latency_ms": latency_ms,
            "detail": str(exc),
        }
