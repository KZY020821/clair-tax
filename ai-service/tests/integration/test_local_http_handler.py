"""Integration tests for FastAPI HTTP endpoints using TestClient."""

import io
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.models.extraction import ExtractionResult


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


@pytest.fixture
def synthetic_receipt_png() -> bytes:
    img = Image.new("RGB", (400, 300), color=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


class TestHealthEndpoint:
    def test_health_returns_200(self, client):
        response = client.get("/health")
        assert response.status_code == 200

    def test_health_returns_status_ok(self, client):
        response = client.get("/health")
        data = response.json()
        assert data["status"] == "ok"

    def test_health_includes_required_fields(self, client):
        response = client.get("/health")
        data = response.json()
        assert "ocr_ready" in data
        assert "model_loaded" in data
        assert "s3_bucket" in data
        assert "backend_url" in data

    def test_health_never_500(self, client):
        """Even if EasyOCR fails, /health must not 500."""
        with patch("app.main.get_ocr_reader", side_effect=RuntimeError("GPU error")):
            response = client.get("/health")
        assert response.status_code == 200


class TestProcessReceiptEndpoint:
    def test_process_receipt_s3_error_returns_200_with_failed_status(self, client):
        from app.clients.s3_storage import StorageError

        with patch(
            "app.main.process_receipt",
            side_effect=StorageError(
                s3_key="receipts/test.jpg",
                operation="get_object",
                cause=Exception("NoSuchKey"),
                not_found=True,
            ),
        ):
            response = client.post(
                "/api/process-receipt",
                json={
                    "receipt_id": "r-001",
                    "s3_key": "receipts/test.jpg",
                    "user_id": "u-1",
                },
            )
        assert response.status_code == 200
        data = response.json()
        assert data["extraction_status"] == "failed"
        assert data["error_detail"] == "s3_error"

    def test_process_receipt_backend_error_returns_200_with_failed_status(self, client):
        from app.clients.backend_api import BackendApiError

        with patch(
            "app.main.process_receipt",
            side_effect=BackendApiError("backend unreachable"),
        ):
            response = client.post(
                "/api/process-receipt",
                json={
                    "receipt_id": "r-002",
                    "s3_key": "receipts/test.jpg",
                    "user_id": "u-1",
                },
            )
        assert response.status_code == 200
        data = response.json()
        assert data["extraction_status"] == "failed"
        assert data["error_detail"] == "backend_error"

    def test_process_receipt_invalid_s3_key_rejected(self, client):
        """s3_key not starting with 'receipts/' should be rejected with 422."""
        response = client.post(
            "/api/process-receipt",
            json={
                "receipt_id": "r-003",
                "s3_key": "invalid/path/file.jpg",
                "user_id": "u-1",
            },
        )
        assert response.status_code == 422

    def test_process_receipt_unexpected_error_returns_500(self, client):
        with patch(
            "app.main.process_receipt",
            side_effect=RuntimeError("completely unexpected"),
        ):
            response = client.post(
                "/api/process-receipt",
                json={
                    "receipt_id": "r-004",
                    "s3_key": "receipts/test.jpg",
                    "user_id": "u-1",
                },
            )
        assert response.status_code == 500

    def test_process_receipt_valid_request_calls_process_receipt(self, client, sample_ocr_blocks):
        fake_result = ExtractionResult(
            receipt_id="r-005",
            extraction_status="extracted",
            amount="32.54",
            currency="MYR",
            date="2024-03-15",
            merchant_name="MYDIN SUPERMARKET SDN BHD",
        )
        with patch("app.main.process_receipt", return_value=fake_result):
            response = client.post(
                "/api/process-receipt",
                json={
                    "receipt_id": "r-005",
                    "s3_key": "receipts/file.jpg",
                    "user_id": "u-2",
                },
            )
        assert response.status_code == 200
        data = response.json()
        assert data["extraction_status"] == "extracted"
        assert data["amount"] == "32.54"


class TestDemoSummaryEndpoint:
    def test_demo_summary_with_valid_image(self, client, synthetic_receipt_png, sample_ocr_blocks):
        with patch("app.main.extract_blocks", return_value=sample_ocr_blocks):
            response = client.post(
                "/api/demo-summary",
                files={"file": ("receipt.png", synthetic_receipt_png, "image/png")},
            )
        assert response.status_code == 200
        data = response.json()
        assert "extraction_status" in data
        assert data["extraction_status"] in ("extracted", "partial", "invalid", "no_text_detected")

    def test_demo_summary_with_invalid_file_returns_422(self, client):
        response = client.post(
            "/api/demo-summary",
            files={"file": ("notanimage.txt", b"this is not an image", "text/plain")},
        )
        assert response.status_code == 422

    def test_demo_summary_no_blocks_returns_no_text_detected(self, client, synthetic_receipt_png):
        with patch("app.main.extract_blocks", return_value=[]):
            response = client.post(
                "/api/demo-summary",
                files={"file": ("receipt.png", synthetic_receipt_png, "image/png")},
            )
        assert response.status_code == 200
        assert response.json()["extraction_status"] == "no_text_detected"

    def test_demo_summary_does_not_require_s3(self, client, synthetic_receipt_png):
        """Demo summary endpoint must not call S3."""
        with patch("app.main.extract_blocks", return_value=[]):
            with patch("app.main.S3StorageClient") as mock_s3:
                response = client.post(
                    "/api/demo-summary",
                    files={"file": ("receipt.png", synthetic_receipt_png, "image/png")},
                )
                mock_s3.assert_not_called()
        assert response.status_code == 200


class TestOcrHealthEndpoint:
    def test_ocr_health_always_200(self, client):
        with patch("app.main.extract_blocks", return_value=[]):
            response = client.get("/api/health/ocr")
        assert response.status_code == 200

    def test_ocr_health_returns_latency(self, client):
        with patch("app.main.extract_blocks", return_value=[]):
            response = client.get("/api/health/ocr")
        data = response.json()
        assert "latency_ms" in data
        assert isinstance(data["latency_ms"], int)

    def test_ocr_health_returns_status_on_error(self, client):
        with patch("app.main.extract_blocks", side_effect=RuntimeError("OCR failed")):
            response = client.get("/api/health/ocr")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "error"
