import time

import boto3
import botocore.exceptions
import structlog

from app.config import Settings

logger = structlog.get_logger(__name__)


class StorageError(Exception):
    def __init__(self, s3_key: str, operation: str, cause: Exception, not_found: bool = False):
        self.s3_key = s3_key
        self.operation = operation
        self.cause = cause
        self.not_found = not_found
        super().__init__(f"S3 {operation} failed for key={s3_key!r}: {cause}")


class S3StorageClient:
    def __init__(self, settings: Settings) -> None:
        self._bucket = settings.S3_BUCKET_NAME
        self._client = boto3.client(
            "s3",
            region_name=settings.AWS_REGION,
            aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
            aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
        )

    def download_receipt(self, s3_key: str) -> bytes:
        start = time.monotonic()
        try:
            response = self._client.get_object(Bucket=self._bucket, Key=s3_key)
            data: bytes = response["Body"].read()
            latency_ms = int((time.monotonic() - start) * 1000)
            logger.debug(
                "s3_download_complete",
                s3_key=s3_key,
                size_bytes=len(data),
                latency_ms=latency_ms,
            )
            return data
        except botocore.exceptions.ClientError as exc:
            error_code = exc.response.get("Error", {}).get("Code", "")
            not_found = error_code in ("NoSuchKey", "404")
            if not_found:
                logger.warning("s3_object_not_found", s3_key=s3_key, error_code=error_code)
            raise StorageError(s3_key=s3_key, operation="get_object", cause=exc, not_found=not_found) from exc
        except Exception as exc:
            raise StorageError(s3_key=s3_key, operation="get_object", cause=exc) from exc

    def upload_receipt(self, s3_key: str, data: bytes, content_type: str) -> str:
        start = time.monotonic()
        try:
            self._client.put_object(
                Bucket=self._bucket,
                Key=s3_key,
                Body=data,
                ContentType=content_type,
            )
            latency_ms = int((time.monotonic() - start) * 1000)
            logger.debug(
                "s3_upload_complete",
                s3_key=s3_key,
                size_bytes=len(data),
                latency_ms=latency_ms,
            )
            return s3_key
        except botocore.exceptions.ClientError as exc:
            raise StorageError(s3_key=s3_key, operation="put_object", cause=exc) from exc
        except Exception as exc:
            raise StorageError(s3_key=s3_key, operation="put_object", cause=exc) from exc
