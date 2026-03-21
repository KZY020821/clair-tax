from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ai-service"}


def test_demo_summary() -> None:
    response = client.get("/api/demo-summary")

    assert response.status_code == 200
    assert response.json() == {
        "service": "ai-service",
        "status": "ready",
        "detected_receipt_count": 3,
        "extracted_total_amount": 1820.5,
        "suggestion_preview": (
            "Lifestyle receipts were grouped successfully and look ready for user review "
            "before tax filing."
        ),
        "generated_at": "2026-03-20T00:00:00Z",
    }
