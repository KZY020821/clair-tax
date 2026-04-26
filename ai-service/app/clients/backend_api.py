import httpx
import structlog

from app.config import Settings
from app.models.extraction import ExtractionResult

logger = structlog.get_logger(__name__)


class BackendApiError(Exception):
    pass


class BackendApiClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.BACKEND_API_BASE_URL
        self._token = settings.BACKEND_INTERNAL_TOKEN

    async def write_extraction_result(self, receipt_id: str, result: ExtractionResult) -> None:
        url = f"{self._base_url}/api/receipts/{receipt_id}/extraction"
        headers: dict[str, str] = {"Content-Type": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"

        payload = result.model_dump(mode="json")

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.put(url, json=payload, headers=headers)

            if 400 <= response.status_code < 500:
                logger.error(
                    "backend_write_4xx",
                    receipt_id=receipt_id,
                    status_code=response.status_code,
                    response_body=response.text[:500],
                )
                return

            if response.status_code >= 500:
                raise BackendApiError(
                    f"Backend returned {response.status_code} for receipt {receipt_id}: {response.text[:200]}"
                )

        except httpx.ConnectError as exc:
            raise BackendApiError(
                f"Cannot reach Spring Boot at {url}. Is the backend running? "
                f"Start it with: cd backend && ./mvnw spring-boot:run"
            ) from exc
        except httpx.TimeoutException as exc:
            raise BackendApiError(
                f"Timeout (>10s) calling backend for receipt {receipt_id} at {url}"
            ) from exc
        except BackendApiError:
            raise
        except Exception as exc:
            raise BackendApiError(f"Unexpected error calling backend for receipt {receipt_id}: {exc}") from exc
