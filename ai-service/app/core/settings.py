from __future__ import annotations

from dataclasses import dataclass
import os


def _env_flag(name: str, default: bool) -> bool:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    backend_api_base_url: str = os.getenv(
        "BACKEND_API_BASE_URL", "http://127.0.0.1:18080"
    ).rstrip("/")
    backend_internal_token: str = os.getenv(
        "BACKEND_INTERNAL_TOKEN", "test-internal-token"
    )
    aws_region: str = os.getenv("AWS_REGION", "ap-southeast-1")
    default_currency: str = os.getenv("DEFAULT_RECEIPT_CURRENCY", "MYR")
    textract_provider_name: str = "aws-textract-analyze-expense"
    textract_provider_version: str = "2026-03-27"
    trained_postprocessor_enabled: bool = _env_flag(
        "TRAINED_RECEIPT_POSTPROCESSOR_ENABLED", False
    )
    trained_postprocessor_artifact_path: str = os.getenv(
        "TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH",
        "ai-service/model_artifacts/receipt_postprocessor.joblib",
    )
    amount_selection_threshold: float = float(
        os.getenv("RECEIPT_AMOUNT_SELECTION_THRESHOLD", "0.65")
    )
    date_selection_threshold: float = float(
        os.getenv("RECEIPT_DATE_SELECTION_THRESHOLD", "0.65")
    )
    validity_threshold: float = float(
        os.getenv("RECEIPT_VALIDITY_THRESHOLD", "0.55")
    )
    minimum_seed_dataset_size: int = int(
        os.getenv("RECEIPT_MINIMUM_SEED_DATASET_SIZE", "75")
    )


def get_settings() -> Settings:
    return Settings()
