"""
CLI tool to prepare receipt annotations from local images or S3.

Usage:
    python -m app.cli.prepare_receipt_annotations --input-dir ./samples --output ./data/annotations.jsonl
    python -m app.cli.prepare_receipt_annotations --input-dir s3://bucket/prefix --output ./data/annotations.jsonl --source s3
"""

import argparse
import os
import sys
import uuid
from pathlib import Path

import structlog

from app.clients.ocr import extract_blocks_from_file
from app.models.training import AnnotatedReceipt, LabeledBlock

logger = structlog.get_logger(__name__)

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp", ".pdf"}


def _process_image(image_bytes: bytes, image_path: str, s3_key: str | None = None) -> AnnotatedReceipt:
    receipt_id = str(uuid.uuid4())
    blocks = extract_blocks_from_file(image_bytes, receipt_id=receipt_id)

    labeled_blocks = [
        LabeledBlock(
            text=b.text,
            confidence=b.confidence,
            bbox=b.bbox,
            line_index=b.line_index,
            is_amount=False,
            is_date=False,
            is_merchant=False,
        )
        for b in blocks
    ]

    return AnnotatedReceipt(
        receipt_id=receipt_id,
        s3_key=s3_key,
        image_path=image_path,
        ground_truth_amount=None,
        ground_truth_date=None,
        ground_truth_merchant=None,
        blocks=labeled_blocks,
    )


def _process_local_dir(input_dir: str, output_path: str) -> None:
    input_path = Path(input_dir)
    if not input_path.exists():
        logger.error("input_dir_not_found", input_dir=input_dir)
        sys.exit(1)

    image_files = [
        f for f in input_path.rglob("*")
        if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS
    ]

    if not image_files:
        logger.warning("no_images_found", input_dir=input_dir)
        os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
        Path(output_path).touch()
        return

    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    written = 0

    with open(output_path, "w", encoding="utf-8") as f:
        for image_file in sorted(image_files):
            try:
                image_bytes = image_file.read_bytes()
                receipt = _process_image(
                    image_bytes=image_bytes,
                    image_path=str(image_file),
                    s3_key=None,
                )
                f.write(receipt.model_dump_json() + "\n")
                written += 1
                logger.info("annotation_written", image_path=str(image_file), receipt_id=receipt.receipt_id)
            except Exception as exc:
                logger.error("annotation_failed", image_path=str(image_file), detail=str(exc))

    logger.info("prepare_complete", output_path=output_path, written=written, total=len(image_files))


def _process_s3(s3_uri: str, output_path: str) -> None:
    from app.clients.s3_storage import S3StorageClient
    from app.config import get_settings

    settings = get_settings()
    client = S3StorageClient(settings)

    # Parse s3://bucket/prefix
    if s3_uri.startswith("s3://"):
        s3_uri = s3_uri[5:]
    parts = s3_uri.split("/", 1)
    bucket = parts[0]
    prefix = parts[1] if len(parts) > 1 else ""

    import boto3

    s3 = boto3.client(
        "s3",
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
    )

    paginator = s3.get_paginator("list_objects_v2")
    pages = paginator.paginate(Bucket=bucket, Prefix=prefix)

    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    written = 0

    with open(output_path, "w", encoding="utf-8") as f:
        for page in pages:
            for obj in page.get("Contents", []):
                key = obj["Key"]
                ext = os.path.splitext(key)[1].lower()
                if ext not in SUPPORTED_EXTENSIONS:
                    continue
                try:
                    image_bytes = client.download_receipt(key)
                    receipt = _process_image(
                        image_bytes=image_bytes,
                        image_path=f"s3://{bucket}/{key}",
                        s3_key=key,
                    )
                    f.write(receipt.model_dump_json() + "\n")
                    written += 1
                    logger.info("annotation_written", s3_key=key, receipt_id=receipt.receipt_id)
                except Exception as exc:
                    logger.error("annotation_failed", s3_key=key, detail=str(exc))

    logger.info("prepare_complete", output_path=output_path, written=written)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare receipt annotations (OCR blocks) for labeling and training."
    )
    parser.add_argument("--input-dir", required=True, help="Local directory or S3 URI of receipt images")
    parser.add_argument("--output", required=True, help="Output JSONL file path")
    parser.add_argument("--source", choices=["local", "s3"], default="local", help="Image source")
    args = parser.parse_args()

    if args.source == "s3":
        _process_s3(args.input_dir, args.output)
    else:
        _process_local_dir(args.input_dir, args.output)


if __name__ == "__main__":
    main()
