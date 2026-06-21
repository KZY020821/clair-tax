import io

import pytest
from moto import mock_aws
from PIL import Image, ImageDraw

from app.clients.ocr import OcrBlock
from app.config import get_settings
from app.models.job import ReceiptJob


@pytest.fixture(autouse=True)
def override_settings(monkeypatch):
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "testing")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "testing")
    monkeypatch.setenv("AWS_REGION", "us-east-1")
    monkeypatch.setenv("S3_BUCKET_NAME", "test-receipts")
    monkeypatch.setenv("BACKEND_API_BASE_URL", "http://mock-backend:8080")
    monkeypatch.setenv("APP_ENV", "local")
    get_settings.cache_clear()


@pytest.fixture
def sample_ocr_blocks() -> list[OcrBlock]:
    """Realistic Malaysian receipt OCR blocks."""
    return [
        OcrBlock(text="MYDIN SUPERMARKET SDN BHD", confidence=0.95, bbox=[[10, 5], [300, 5], [300, 20], [10, 20]], line_index=0),
        OcrBlock(text="NO 12 JALAN MASJID INDIA", confidence=0.90, bbox=[[10, 25], [300, 25], [300, 40], [10, 40]], line_index=1),
        OcrBlock(text="KUALA LUMPUR 50100", confidence=0.88, bbox=[[10, 45], [300, 45], [300, 60], [10, 60]], line_index=2),
        OcrBlock(text="TEL: 03-26929345", confidence=0.85, bbox=[[10, 65], [300, 65], [300, 80], [10, 80]], line_index=3),
        OcrBlock(text="DATE: 15/03/2024", confidence=0.92, bbox=[[10, 90], [300, 90], [300, 105], [10, 105]], line_index=4),
        OcrBlock(text="TIME: 14:32:05", confidence=0.89, bbox=[[10, 110], [300, 110], [300, 125], [10, 125]], line_index=5),
        OcrBlock(text="RECEIPT NO: 00123456", confidence=0.91, bbox=[[10, 130], [300, 130], [300, 145], [10, 145]], line_index=6),
        OcrBlock(text="BERAS FAIZA 5KG", confidence=0.87, bbox=[[10, 160], [200, 160], [200, 175], [10, 175]], line_index=7),
        OcrBlock(text="15.90", confidence=0.93, bbox=[[220, 160], [300, 160], [300, 175], [220, 175]], line_index=7),
        OcrBlock(text="MINYAK MASAK 1L", confidence=0.86, bbox=[[10, 180], [200, 180], [200, 195], [10, 195]], line_index=8),
        OcrBlock(text="8.50", confidence=0.90, bbox=[[220, 180], [300, 180], [300, 195], [220, 195]], line_index=8),
        OcrBlock(text="SUSU DUTCH LADY", confidence=0.88, bbox=[[10, 200], [200, 200], [200, 215], [10, 215]], line_index=9),
        OcrBlock(text="6.30", confidence=0.91, bbox=[[220, 200], [300, 200], [300, 215], [220, 215]], line_index=9),
        OcrBlock(text="SST (6%)", confidence=0.85, bbox=[[10, 230], [200, 230], [200, 245], [10, 245]], line_index=10),
        OcrBlock(text="1.84", confidence=0.88, bbox=[[220, 230], [300, 230], [300, 245], [220, 245]], line_index=10),
        OcrBlock(text="JUMLAH / TOTAL", confidence=0.96, bbox=[[10, 260], [200, 260], [200, 280], [10, 280]], line_index=11),
        OcrBlock(text="32.54", confidence=0.97, bbox=[[220, 260], [300, 260], [300, 280], [220, 280]], line_index=11),
        OcrBlock(text="CASH", confidence=0.92, bbox=[[10, 295], [100, 295], [100, 310], [10, 310]], line_index=12),
        OcrBlock(text="50.00", confidence=0.93, bbox=[[220, 295], [300, 295], [300, 310], [220, 310]], line_index=12),
        OcrBlock(text="BAKI / CHANGE", confidence=0.91, bbox=[[10, 320], [200, 320], [200, 335], [10, 335]], line_index=13),
        OcrBlock(text="17.46", confidence=0.94, bbox=[[220, 320], [300, 320], [300, 335], [220, 335]], line_index=13),
        OcrBlock(text="TERIMA KASIH", confidence=0.89, bbox=[[50, 355], [250, 355], [250, 370], [50, 370]], line_index=14),
    ]


@pytest.fixture
def sample_receipt_image() -> bytes:
    """Generate a synthetic receipt PNG using Pillow — no disk I/O."""
    img = Image.new("RGB", (400, 600), color=(255, 255, 255))
    draw = ImageDraw.Draw(img)

    lines = [
        "MYDIN SUPERMARKET SDN BHD",
        "NO 12 JALAN MASJID INDIA",
        "KUALA LUMPUR 50100",
        "",
        "DATE: 15/03/2024  TIME: 14:32",
        "",
        "BERAS FAIZA 5KG        RM 15.90",
        "MINYAK MASAK 1L        RM  8.50",
        "SUSU DUTCH LADY        RM  6.30",
        "",
        "SST (6%)               RM  1.84",
        "JUMLAH / TOTAL         RM 32.54",
        "",
        "CASH                   RM 50.00",
        "BAKI / CHANGE          RM 17.46",
        "",
        "TERIMA KASIH",
    ]

    y = 20
    for line in lines:
        draw.text((20, y), line, fill=(0, 0, 0))
        y += 32

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture
def mock_s3(sample_receipt_image):
    """Moto mock S3 with test-receipts bucket and a pre-loaded receipt."""
    import boto3

    with mock_aws():
        s3 = boto3.client(
            "s3",
            region_name="us-east-1",
            aws_access_key_id="testing",
            aws_secret_access_key="testing",
        )
        s3.create_bucket(Bucket="test-receipts")
        s3.put_object(
            Bucket="test-receipts",
            Key="receipts/test.jpg",
            Body=sample_receipt_image,
            ContentType="image/png",
        )
        yield s3


@pytest.fixture
def mock_backend_api(respx_mock):
    """Mock backend PUT /api/receipts/{id}/extraction → 200."""

    import httpx

    respx_mock.put(
        url__regex=r"http://mock-backend:8080/api/receipts/.+/extraction"
    ).mock(return_value=httpx.Response(200, json={"status": "ok"}))
    return respx_mock


@pytest.fixture
def fake_receipt_job() -> ReceiptJob:
    return ReceiptJob(
        receipt_id="receipt-abc-123",
        s3_key="receipts/test.jpg",
        user_id="user-1",
    )
