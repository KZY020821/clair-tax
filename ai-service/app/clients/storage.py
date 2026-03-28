from __future__ import annotations

from dataclasses import dataclass

from app.core.settings import Settings


class StorageClientError(RuntimeError):
    pass


@dataclass
class S3StorageClient:
    settings: Settings

    def get_object_bytes(self, bucket: str, key: str) -> bytes:
        try:
            import boto3
            from botocore.exceptions import BotoCoreError, ClientError
        except ImportError as exc:
            raise StorageClientError("boto3 is required to fetch S3 objects") from exc

        client = boto3.client("s3", region_name=self.settings.aws_region)

        try:
            response = client.get_object(Bucket=bucket, Key=key)
        except (BotoCoreError, ClientError) as exc:
            raise StorageClientError(f"Unable to load s3://{bucket}/{key}") from exc

        body = response["Body"].read()
        if not isinstance(body, bytes):
            raise StorageClientError("S3 object response body was not bytes")

        return body
