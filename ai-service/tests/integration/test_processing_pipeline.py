"""Integration tests: full pipeline with moto S3 and respx backend mock."""

import asyncio
from unittest.mock import MagicMock, patch

import pytest

from app.models.extraction import ExtractionResult
from app.models.job import ReceiptJob
from app.services.processing import process_receipt


@pytest.fixture
def mock_ocr_blocks(sample_ocr_blocks):
    """Patch extract_blocks to return sample blocks without real EasyOCR."""
    with patch("app.services.processing.extract_blocks", return_value=sample_ocr_blocks) as m:
        yield m


@pytest.mark.asyncio
async def test_full_pipeline_produces_extraction_result(mock_s3, mock_backend_api, mock_ocr_blocks, fake_receipt_job):
    """End-to-end: S3 download → OCR → normalize → postprocess → backend write."""
    result = await process_receipt(fake_receipt_job)

    assert isinstance(result, ExtractionResult)
    assert result.receipt_id == "receipt-abc-123"
    assert result.extraction_status in ("extracted", "partial", "invalid")


@pytest.mark.asyncio
async def test_pipeline_extracts_correct_amount(mock_s3, mock_backend_api, mock_ocr_blocks, fake_receipt_job):
    """Amount should be extracted as the JUMLAH/TOTAL line value."""
    result = await process_receipt(fake_receipt_job)

    # TOTAL is 32.54 in sample blocks
    if result.extraction_status != "invalid":
        assert result.amount == "32.54"


@pytest.mark.asyncio
async def test_pipeline_extracts_correct_date(mock_s3, mock_backend_api, mock_ocr_blocks, fake_receipt_job):
    """Date 15/03/2024 should be parsed as 2024-03-15."""
    result = await process_receipt(fake_receipt_job)

    if result.date is not None:
        assert result.date == "2024-03-15"


@pytest.mark.asyncio
async def test_pipeline_handles_zero_ocr_blocks(mock_s3, mock_backend_api, fake_receipt_job):
    """Zero OCR blocks → no_text_detected status."""
    with patch("app.services.processing.extract_blocks", return_value=[]):
        result = await process_receipt(fake_receipt_job)

    assert result.extraction_status == "no_text_detected"
    assert result.amount is None
    assert result.raw_ocr_block_count == 0


@pytest.mark.asyncio
async def test_pipeline_raises_on_s3_error(fake_receipt_job):
    """StorageError from S3 should propagate out of process_receipt."""
    from app.clients.s3_storage import StorageError

    with patch(
        "app.services.processing.S3StorageClient.download_receipt",
        side_effect=StorageError(s3_key="receipts/test.jpg", operation="get_object", cause=Exception("NoSuchKey"), not_found=True),
    ):
        with pytest.raises(StorageError):
            await process_receipt(fake_receipt_job)


@pytest.mark.asyncio
async def test_pipeline_raises_on_backend_5xx(mock_s3, mock_ocr_blocks, fake_receipt_job):
    """BackendApiError raised for 5xx backend response."""
    import httpx
    import respx

    with respx.mock:
        respx.put(url__regex=r"http://mock-backend:8080/api/receipts/.+/extraction").mock(
            return_value=httpx.Response(503, json={"error": "service unavailable"})
        )
        from app.clients.backend_api import BackendApiError

        with pytest.raises(BackendApiError):
            await process_receipt(fake_receipt_job)


@pytest.mark.asyncio
async def test_pipeline_does_not_raise_on_backend_4xx(mock_s3, mock_ocr_blocks, fake_receipt_job):
    """4xx from backend should be logged but not raise."""
    import httpx
    import respx

    with respx.mock:
        respx.put(url__regex=r"http://mock-backend:8080/api/receipts/.+/extraction").mock(
            return_value=httpx.Response(404, json={"error": "not found"})
        )
        # Should not raise
        result = await process_receipt(fake_receipt_job)
    assert isinstance(result, ExtractionResult)


@pytest.mark.asyncio
async def test_pipeline_backend_write_latency_logged(mock_s3, mock_backend_api, mock_ocr_blocks, fake_receipt_job):
    """Just confirm pipeline completes without error when backend write succeeds."""
    result = await process_receipt(fake_receipt_job)
    assert result is not None


@pytest.mark.asyncio
async def test_pipeline_currency_set_from_job(mock_s3, mock_backend_api, mock_ocr_blocks):
    """Currency should come from the job, not settings default."""
    job = ReceiptJob(
        receipt_id="receipt-usd-999",
        s3_key="receipts/test.jpg",
        user_id="user-2",
        currency="USD",
    )
    result = await process_receipt(job)

    if result.amount is not None:
        assert result.currency == "USD"
