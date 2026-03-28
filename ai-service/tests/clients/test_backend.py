from __future__ import annotations

import json
from datetime import UTC, date, datetime
from decimal import Decimal

import httpx

from app.clients.backend import BackendClient
from app.core.settings import Settings
from app.models.extraction import ExtractionWarning, NormalizedExtractionResult


def test_submit_extraction_result_shapes_request_payload() -> None:
    captured_request: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured_request["url"] = str(request.url)
        captured_request["headers"] = dict(request.headers)
        captured_request["json"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(status_code=202)

    client = httpx.Client(transport=httpx.MockTransport(handler))
    backend_client = BackendClient(
        Settings(backend_api_base_url="http://backend.test", backend_internal_token="secret"),
        client=client,
    )

    backend_client.submit_extraction_result(
        "receipt-123",
        "job-123",
        NormalizedExtractionResult(
            receipt_id="receipt-123",
            total_amount=Decimal("89.90"),
            receipt_date=date(2025, 5, 12),
            merchant_name="Bookshop",
            currency="MYR",
            confidence_score=Decimal("0.9500"),
            warnings=[
                ExtractionWarning(
                    code="currency_defaulted", message="Currency defaulted to MYR."
                )
            ],
            raw_payload_json={"provider_payload": {"ExpenseDocuments": []}},
            provider_name="aws-textract-analyze-expense",
            provider_version="2026-03-27+receipt-postprocessor:test",
            processed_at=datetime(2026, 3, 27, 0, 0, tzinfo=UTC),
            error_code="low_confidence_extraction",
            error_message="The extraction confidence stayed below the review threshold.",
        ),
    )

    assert captured_request["url"] == "http://backend.test/api/internal/receipts/receipt-123/extraction-results"
    assert captured_request["headers"]["x-clair-internal-token"] == "secret"
    assert captured_request["json"] == {
        "jobId": "job-123",
        "totalAmount": "89.90",
        "receiptDate": "2025-05-12",
        "merchantName": "Bookshop",
        "currency": "MYR",
        "confidenceScore": "0.9500",
        "warnings": ["Currency defaulted to MYR."],
        "rawPayloadJson": '{"provider_payload": {"ExpenseDocuments": []}}',
        "providerName": "aws-textract-analyze-expense",
        "providerVersion": "2026-03-27+receipt-postprocessor:test",
        "processedAt": "2026-03-27T00:00:00Z",
        "errorCode": "low_confidence_extraction",
        "errorMessage": "The extraction confidence stayed below the review threshold.",
    }
