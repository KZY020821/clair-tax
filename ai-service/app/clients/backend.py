from __future__ import annotations

from dataclasses import dataclass
import json
from typing import Any

import httpx

from app.core.settings import Settings
from app.models.extraction import NormalizedExtractionResult


class BackendClientError(RuntimeError):
    def __init__(self, message: str, *, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass
class BackendClient:
    settings: Settings
    client: httpx.Client | None = None

    @property
    def _client(self) -> httpx.Client:
        if self.client is None:
            self.client = httpx.Client(timeout=20.0)
        return self.client

    def _headers(self) -> dict[str, str]:
        return {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-Clair-Internal-Token": self.settings.backend_internal_token,
        }

    def record_processing_attempt(
        self,
        receipt_id: str,
        job_id: str,
        status: str,
        *,
        error_code: str | None = None,
        error_message: str | None = None,
    ) -> None:
        self._post(
            f"/api/internal/receipts/{receipt_id}/processing-attempts",
            {
                "jobId": job_id,
                "status": status,
                "errorCode": error_code,
                "errorMessage": error_message,
            },
        )

    def submit_extraction_result(
        self, receipt_id: str, job_id: str, result: NormalizedExtractionResult
    ) -> None:
        self._post(
            f"/api/internal/receipts/{receipt_id}/extraction-results",
            {
                "jobId": job_id,
                "totalAmount": None if result.total_amount is None else str(result.total_amount),
                "receiptDate": None
                if result.receipt_date is None
                else result.receipt_date.isoformat(),
                "merchantName": result.merchant_name,
                "currency": result.currency,
                "confidenceScore": str(result.confidence_score),
                "warnings": [warning.message for warning in result.warnings],
                "rawPayloadJson": json.dumps(result.raw_payload_json),
                "providerName": result.provider_name,
                "providerVersion": result.provider_version,
                "processedAt": result.processed_at.isoformat().replace("+00:00", "Z"),
                "errorCode": result.error_code,
                "errorMessage": result.error_message,
            },
        )

    def _post(self, path: str, payload: dict[str, Any]) -> None:
        url = f"{self.settings.backend_api_base_url}{path}"
        response = self._client.post(url, headers=self._headers(), json=payload)
        if response.status_code >= 500:
            raise BackendClientError(
                f"Backend writeback failed with {response.status_code}",
                status_code=response.status_code,
            )
        if response.status_code >= 400:
            raise BackendClientError(
                f"Backend rejected writeback with {response.status_code}",
                status_code=response.status_code,
            )
