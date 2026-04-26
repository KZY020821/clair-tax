"""
CLI tool to train receipt extraction models from labeled annotations.

Usage:
    python -m app.cli.train_receipt_models \\
        --manifest ./data/labeled.jsonl \\
        --output ./model_artifacts/receipt_postprocessor.joblib \\
        --candidate-table-dir ./data/tables
"""

import argparse
import json
import os
import sys

import structlog

from app.models.training import AnnotatedReceipt
from app.training.trainer import train

logger = structlog.get_logger(__name__)


def _load_manifest(manifest_path: str) -> list[AnnotatedReceipt]:
    if not os.path.exists(manifest_path):
        logger.error("manifest_not_found", manifest_path=manifest_path)
        sys.exit(1)

    receipts: list[AnnotatedReceipt] = []
    errors = 0

    with open(manifest_path, "r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                receipt = AnnotatedReceipt(**data)
                receipts.append(receipt)
            except Exception as exc:
                logger.error("manifest_parse_error", line_no=line_no, detail=str(exc))
                errors += 1

    logger.info(
        "manifest_loaded",
        manifest_path=manifest_path,
        n_receipts=len(receipts),
        n_errors=errors,
    )
    return receipts


def _export_candidate_tables(receipts: list[AnnotatedReceipt], table_dir: str) -> None:
    """Export CSV candidate tables per field for inspection."""
    import csv

    from app.clients.ocr import OcrBlock
    from app.services.normalization import (
        extract_amount_candidates,
        extract_date_candidates,
        extract_merchant_candidates,
    )

    os.makedirs(table_dir, exist_ok=True)

    extractors = {
        "amount": extract_amount_candidates,
        "date": extract_date_candidates,
        "merchant": extract_merchant_candidates,
    }

    for field, extractor in extractors.items():
        table_path = os.path.join(table_dir, f"{field}_candidates.csv")
        rows = []
        for receipt in receipts:
            ocr_blocks = [
                OcrBlock(
                    text=b.text,
                    confidence=b.confidence,
                    bbox=b.bbox,
                    line_index=b.line_index,
                )
                for b in receipt.blocks
            ]
            candidates = extractor(ocr_blocks)
            ground_truth = getattr(receipt, f"ground_truth_{field}", None)
            for c in candidates:
                rows.append(
                    {
                        "receipt_id": receipt.receipt_id,
                        "candidate_value": c.value,
                        "heuristic_score": c.heuristic_score,
                        "confidence": c.confidence,
                        "is_correct": c.value == ground_truth,
                        "ground_truth": ground_truth or "",
                    }
                )

        if rows:
            with open(table_path, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=rows[0].keys())
                writer.writeheader()
                writer.writerows(rows)
            logger.info("candidate_table_exported", field=field, path=table_path, n_rows=len(rows))


def main() -> None:
    parser = argparse.ArgumentParser(description="Train receipt extraction models.")
    parser.add_argument("--manifest", required=True, help="Labeled JSONL manifest file")
    parser.add_argument("--output", required=True, help="Output .joblib artifact path")
    parser.add_argument("--candidate-table-dir", default=None, help="Directory to export candidate CSV tables")
    args = parser.parse_args()

    receipts = _load_manifest(args.manifest)
    if not receipts:
        logger.error("no_receipts_to_train")
        sys.exit(1)

    train(receipts, args.output)

    if args.candidate_table_dir:
        _export_candidate_tables(receipts, args.candidate_table_dir)

    logger.info("train_complete", output=args.output)


if __name__ == "__main__":
    main()
