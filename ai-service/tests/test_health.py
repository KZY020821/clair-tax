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
            "Queued receipt uploads move through extraction and wait for human review "
            "before contributing to tax relief totals."
        ),
        "generated_at": "2026-03-27T00:00:00Z",
    }
