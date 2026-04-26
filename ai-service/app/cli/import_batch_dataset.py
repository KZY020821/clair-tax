"""
CLI tool to build a labeled JSONL from a batch dataset directory.

Usage:
    python -m app.cli.import_batch_dataset \
        --batch-dir ./samples/batch_1/batch_1 \
        --output ./data/labeled.jsonl
"""

import argparse
import csv
import json
import os
import sys
import uuid
from datetime import datetime
from pathlib import Path

import structlog

from app.clients.ocr import extract_blocks_from_file
from app.models.training import AnnotatedReceipt, LabeledBlock

logger = structlog.get_logger(__name__)

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp", ".pdf"}


def _parse_ground_truth(json_str: str) -> dict | None:
    """Parse Json Data field. Returns {merchant, date, amount} or None if invalid."""
    if not json_str.strip():
        return None
    try:
        data = json.loads(json_str)
    except json.JSONDecodeError:
        return None

    merchant = (data.get("invoice") or {}).get("seller_name", "").strip()
    raw_date = (data.get("invoice") or {}).get("invoice_date", "").strip()
    amount = (data.get("subtotal") or {}).get("total", "").strip()

    if not merchant or not raw_date or not amount:
        return None

    try:
        iso_date = datetime.strptime(raw_date, "%m/%d/%Y").strftime("%Y-%m-%d")
    except ValueError:
        return None

    return {"merchant": merchant, "date": iso_date, "amount": amount}


def _build_ground_truth_map(batch_dir: Path) -> dict[str, dict]:
    """Read all CSVs in batch_dir; return {basename -> ground_truth} dict."""
    gt_map: dict[str, dict] = {}
    for csv_path in sorted(batch_dir.glob("*.csv")):
        with open(csv_path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                filename = (row.get("File Name") or "").strip()
                if not filename:
                    continue
                gt = _parse_ground_truth(row.get("Json Data") or "")
                if gt:
                    gt_map[filename] = gt
    return gt_map


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build labeled JSONL from batch dataset CSVs and images."
    )
    parser.add_argument("--batch-dir", required=True, help="Directory with CSV files and image subdirectories")
    parser.add_argument("--output", required=True, help="Output labeled JSONL path")
    args = parser.parse_args()

    batch_path = Path(args.batch_dir)
    if not batch_path.exists():
        logger.error("batch_dir_not_found", batch_dir=args.batch_dir)
        sys.exit(1)

    gt_map = _build_ground_truth_map(batch_path)
    logger.info("ground_truth_loaded", n_entries=len(gt_map))

    image_files = [
        f for f in batch_path.rglob("*")
        if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS
    ]

    os.makedirs(os.path.dirname(args.output) if os.path.dirname(args.output) else ".", exist_ok=True)

    written = skipped_no_csv = failed_ocr = 0

    with open(args.output, "w", encoding="utf-8") as out_f:
        for image_file in sorted(image_files):
            basename = image_file.name
            gt = gt_map.get(basename)
            if gt is None:
                skipped_no_csv += 1
                continue

            receipt_id = str(uuid.uuid4())
            try:
                image_bytes = image_file.read_bytes()
                blocks = extract_blocks_from_file(image_bytes, receipt_id=receipt_id)
            except Exception as exc:
                logger.error("ocr_failed", image_path=str(image_file), detail=str(exc))
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
                image_path=str(image_file),
                ground_truth_amount=gt["amount"],
                ground_truth_date=gt["date"],
                ground_truth_merchant=gt["merchant"],
                blocks=labeled_blocks,
            )
            out_f.write(receipt.model_dump_json() + "\n")
            written += 1
            logger.info("receipt_written", image_path=str(image_file), receipt_id=receipt_id)

    logger.info(
        "import_complete",
        output=args.output,
        written=written,
        skipped_no_csv=skipped_no_csv,
        failed_ocr=failed_ocr,
        total_images=len(image_files),
    )


if __name__ == "__main__":
    main()
