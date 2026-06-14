import json

import httpx
import structlog

from app.config import Settings

logger = structlog.get_logger(__name__)


class ToolExecutionError(Exception):
    pass


class ToolExecutor:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.BACKEND_API_BASE_URL.rstrip("/")
        self._token = settings.BACKEND_INTERNAL_TOKEN
        self._timeout = 15.0

    def _internal_headers(self, user_id: str) -> dict[str, str]:
        return {
            "X-Clair-Internal-Token": self._token,
            "X-User-Id": user_id,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def execute(self, tool_name: str, tool_args: dict, user_id: str) -> dict:
        """
        Dispatches to the correct Spring Boot endpoint.
        Returns a JSON-serialisable dict to be passed back to the LLM as a tool result.
        Raises ToolExecutionError on failure.
        """
        handler = {
            "get_relief_categories": self._get_relief_categories,
            "get_user_year_summary": self._get_user_year_summary,
            "update_profile": self._update_profile,
            "assign_receipt_to_year": self._assign_receipt_to_year,
            "process_receipt_attachment": self._process_receipt_attachment,
        }.get(tool_name)

        if handler is None:
            raise ToolExecutionError(f"Unknown tool: {tool_name}")

        return await handler(tool_args, user_id)

    async def _get(self, path: str, user_id: str, timeout: float | None = None) -> dict:
        url = f"{self._base_url}{path}"
        try:
            async with httpx.AsyncClient(timeout=timeout or self._timeout) as client:
                response = await client.get(url, headers=self._internal_headers(user_id))
        except httpx.TimeoutException as exc:
            raise ToolExecutionError(f"GET {path} timed out") from exc
        except httpx.ConnectError as exc:
            raise ToolExecutionError(f"GET {path} connection failed: {exc}") from exc
        if not response.is_success:
            raise ToolExecutionError(
                f"GET {path} returned {response.status_code}: {response.text[:200]}"
            )
        return response.json()

    async def _put(self, path: str, payload: dict, user_id: str, timeout: float | None = None) -> dict:
        url = f"{self._base_url}{path}"
        try:
            async with httpx.AsyncClient(timeout=timeout or self._timeout) as client:
                response = await client.put(
                    url,
                    content=json.dumps(payload),
                    headers=self._internal_headers(user_id),
                )
        except httpx.TimeoutException as exc:
            raise ToolExecutionError(f"PUT {path} timed out") from exc
        except httpx.ConnectError as exc:
            raise ToolExecutionError(f"PUT {path} connection failed: {exc}") from exc
        if not response.is_success:
            raise ToolExecutionError(
                f"PUT {path} returned {response.status_code}: {response.text[:200]}"
            )
        return response.json()

    async def _post(self, path: str, payload: dict, user_id: str, timeout: float | None = None) -> dict:
        url = f"{self._base_url}{path}"
        try:
            async with httpx.AsyncClient(timeout=timeout or self._timeout) as client:
                response = await client.post(
                    url,
                    content=json.dumps(payload),
                    headers=self._internal_headers(user_id),
                )
        except httpx.TimeoutException as exc:
            raise ToolExecutionError(f"POST {path} timed out") from exc
        except httpx.ConnectError as exc:
            raise ToolExecutionError(f"POST {path} connection failed: {exc}") from exc
        if not response.is_success:
            raise ToolExecutionError(
                f"POST {path} returned {response.status_code}: {response.text[:200]}"
            )
        return response.json()

    async def _get_relief_categories(self, args: dict, user_id: str) -> dict:
        year = args["year"]
        return await self._get(f"/api/policies/{year}", user_id)

    async def _get_user_year_summary(self, args: dict, user_id: str) -> dict:
        year = args["year"]
        return await self._get(f"/api/user-years/{year}", user_id)

    async def _update_profile(self, args: dict, user_id: str) -> dict:
        payload: dict = {
            "isDisabled": args["is_disabled"],
            "maritalStatus": args["marital_status"],
        }
        if "spouse_disabled" in args:
            payload["spouseDisabled"] = args["spouse_disabled"]
        if "spouse_working" in args:
            payload["spouseWorking"] = args["spouse_working"]
        if "has_children" in args:
            payload["hasChildren"] = args["has_children"]
        return await self._put("/api/profile", payload, user_id)

    async def _assign_receipt_to_year(self, args: dict, user_id: str) -> dict:
        payload: dict = {
            "receiptId": args["receipt_id"],
            "year": args["year"],
        }
        if "relief_category_id" in args and args["relief_category_id"]:
            payload["reliefCategoryId"] = args["relief_category_id"]
        return await self._post("/api/internal/chat/receipts", payload, user_id)

    async def _process_receipt_attachment(self, args: dict, user_id: str) -> dict:
        s3_key = args.get("attachment_s3_key")
        if not s3_key:
            raise ToolExecutionError(
                "No receipt attachment found. Please re-upload the file and try again."
            )
        payload: dict = {
            "s3Key": s3_key,
            "year": args["year"],
        }
        if args.get("relief_category_hint"):
            payload["reliefCategoryHint"] = args["relief_category_hint"]
        # OCR can take up to 30s; use a longer timeout than the default 15s
        return await self._post("/api/internal/chat/receipts/process", payload, user_id, timeout=60.0)
