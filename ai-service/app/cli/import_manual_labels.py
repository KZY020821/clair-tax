"""
CLI tool to build a labeled JSONL from a sidecar labels CSV + image directory.

Usage:
    python -m app.cli.import_manual_labels \
        --image-dir ./samples/malaysian \
        --labels-csv ./samples/malaysian/labels.csv \
        --output ./data/labeled_malaysian.jsonl
"""

import argparse
import csv
import os
import sys
import uuid
from pathlib import Path

import structlog

from app.clients.ocr import extract_blocks_from_file
from app.models.training import AnnotatedReceipt, LabeledBlock

logger = structlog.get_logger(__name__)

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp", ".pdf"}


def _build_image_index(image_dir: Path) -> tuple[dict[str, Path], dict[str, Path]]:
    """Return (exact_name → path, stem → path) for all images in image_dir."""
    by_name: dict[str, Path] = {}
    by_stem: dict[str, Path] = {}
    for f in image_dir.iterdir():
        if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS:
            by_name[f.name] = f
            by_stem[f.stem] = f
    return by_name, by_stem


def _resolve_image(file_name: str, by_name: dict, by_stem: dict) -> Path | None:
    """Match CSV file_name to an actual file — exact first, then stem-only fallback."""
    if file_name in by_name:
        return by_name[file_name]
    stem = Path(file_name).stem
    return by_stem.get(stem)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build labeled JSONL from a sidecar labels CSV and image directory."
    )
    parser.add_argument("--image-dir", required=True, help="Directory containing receipt images")
    parser.add_argument("--labels-csv", required=True, help="CSV with columns: file_name, merchant, amount, date")
    parser.add_argument("--output", required=True, help="Output labeled JSONL path")
    args = parser.parse_args()

    image_dir = Path(args.image_dir)
    if not image_dir.exists():
        logger.error("image_dir_not_found", path=args.image_dir)
        sys.exit(1)

    by_name, by_stem = _build_image_index(image_dir)
    logger.info("image_index_built", total_images=len(by_name))

    os.makedirs(os.path.dirname(args.output) if os.path.dirname(args.output) else ".", exist_ok=True)

    written = skipped = failed_ocr = 0

    with open(args.labels_csv, encoding="utf-8-sig", newline="") as csv_f, \
         open(args.output, "w", encoding="utf-8") as out_f:

        reader = csv.DictReader(csv_f)
        for row in reader:
            file_name = (row.get("file_name") or "").strip()
            merchant  = (row.get("merchant")  or "").strip()
            amount    = (row.get("amount")    or "").strip()
            date      = (row.get("date")      or "").strip()

            if not all([file_name, merchant, amount, date]):
                logger.warning("row_incomplete", file_name=file_name)
                skipped += 1
                continue

            image_path = _resolve_image(file_name, by_name, by_stem)
            if image_path is None:
                logger.warning("image_not_found", file_name=file_name)
                skipped += 1
                continue

            receipt_id = str(uuid.uuid4())
            try:
                blocks = extract_blocks_from_file(image_path.read_bytes(), receipt_id=receipt_id)
            except Exception as exc:
                logger.error("ocr_failed", file_name=file_name, detail=str(exc))
                failed_ocr += 1
                continue

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

            receipt = AnnotatedReceipt(
                receipt_id=receipt_id,
                s3_key=None,
                image_path=str(image_path),
                ground_truth_amount=amount,
                ground_truth_date=date,
                ground_truth_merchant=merchant,
                blocks=labeled_blocks,
            )
            out_f.write(receipt.model_dump_json() + "\n")
            written += 1
            logger.info("receipt_written", file_name=file_name, receipt_id=receipt_id)

    logger.info(
        "import_complete",
        output=args.output,
        written=written,
        skipped=skipped,
        failed_ocr=failed_ocr,
    )


if __name__ == "__main__":
    main()
