from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    APP_ENV: Literal["local", "production"] = "local"
    BACKEND_API_BASE_URL: str = "http://localhost:8080"
    BACKEND_INTERNAL_TOKEN: str = ""
    AWS_ACCESS_KEY_ID: str
    AWS_SECRET_ACCESS_KEY: str
    AWS_REGION: str = "us-east-1"
    S3_BUCKET_NAME: str
    EASYOCR_GPU: bool = False
    EASYOCR_MODEL_DIR: str = "/tmp/easyocr_models"
    DEFAULT_RECEIPT_CURRENCY: str = "MYR"
    TRAINED_RECEIPT_POSTPROCESSOR_ENABLED: bool = False
    TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH: str = "./model_artifacts/receipt_postprocessor.joblib"
    RECEIPT_AMOUNT_SELECTION_THRESHOLD: float = 0.5
    RECEIPT_DATE_SELECTION_THRESHOLD: float = 0.4
    RECEIPT_VALIDITY_THRESHOLD: float = 0.3
    LOG_LEVEL: str = "INFO"


@lru_cache
def get_settings() -> Settings:
    return Settings()
