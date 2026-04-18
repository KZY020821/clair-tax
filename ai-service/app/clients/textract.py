from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.core.settings import Settings


class ExtractionProviderError(RuntimeError):
    pass


@dataclass
class TextractExpenseClient:
    settings: Settings

    def analyze_expense(self, document_bytes: bytes, mime_type: str) -> dict[str, Any]:
        try:
            import boto3
            from botocore.exceptions import BotoCoreError, ClientError
        except ImportError as exc:
            raise ExtractionProviderError("boto3 is required to call Textract") from exc

        client = boto3.client("textract", region_name=self.settings.aws_region)

        try:
            response = client.analyze_expense(
                Document={"Bytes": document_bytes},
            )
        except (BotoCoreError, ClientError) as exc:
            raise ExtractionProviderError(
                f"Unable to analyze receipt via Textract ({mime_type})"
            ) from exc

        if not isinstance(response, dict):
            raise ExtractionProviderError("Textract returned an invalid response payload")

        return response
