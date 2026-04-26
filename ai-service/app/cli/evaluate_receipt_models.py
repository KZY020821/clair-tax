"""
CLI tool to evaluate a trained receipt extraction model.

Usage:
    python -m app.cli.evaluate_receipt_models \\
        --manifest ./data/labeled.jsonl \\
        --artifact ./model_artifacts/receipt_postprocessor.joblib
"""

import argparse
import json
import os
import sys

import structlog

from app.models.training import AnnotatedReceipt
from app.training.trainer import evaluate

logger = structlog.get_logger(__name__)

THRESHOLDS = {
    "amount": 0.50,
    "date": 0.40,
    "merchant": 0.40,
}


def _load_manifest(manifest_path: str) -> list[AnnotatedReceipt]:
    if not os.path.exists(manifest_path):
        logger.error("manifest_not_found", manifest_path=manifest_path)
        sys.exit(1)

    receipts: list[AnnotatedReceipt] = []
    with open(manifest_path, "r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                receipts.append(AnnotatedReceipt(**data))
            except Exception as exc:
                logger.error("manifest_parse_error", line_no=line_no, detail=str(exc))
    return receipts


def _print_table(results: dict) -> None:
    header = f"{'Field':<12}{'Precision':>10}{'Recall':>8}{'F1':>8}{'Threshold':>12}"
    separator = "-" * len(header)
    print(separator)
    print(header)
    print(separator)

    for field in ("amount", "date", "merchant"):
        m = results.get(field, {})
        threshold = THRESHOLDS.get(field, 0.40)
        print(
            f"{field:<12}"
            f"{m.get('precision', 0):.2f}      "
            f"{m.get('recall', 0):.2f}  "
            f"{m.get('f1', 0):.2f}  "
            f"{threshold:>10.2f}"
        )

    overall = results.get("overall", {})
    print(separator)
    print(
        f"{'overall':<12}"
        f"{overall.get('precision', 0):.2f}      "
        f"{overall.get('recall', 0):.2f}  "
        f"{overall.get('f1', 0):.2f}"
    )
    print(separator)


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate receipt extraction model.")
    parser.add_argument("--manifest", required=True, help="Labeled JSONL manifest file")
    parser.add_argument("--artifact", required=True, help="Path to .joblib model artifact")
    args = parser.parse_args()

    receipts = _load_manifest(args.manifest)
    if not receipts:
        logger.error("no_receipts_to_evaluate")
        sys.exit(1)

    if not os.path.exists(args.artifact):
        logger.error("artifact_not_found", artifact_path=args.artifact)
        sys.exit(1)

    results = evaluate(receipts, args.artifact)
    _print_table(results)

    logger.info(
        "evaluation_complete",
        artifact=args.artifact,
        n_receipts=len(receipts),
        overall_f1=results.get("overall", {}).get("f1", 0.0),
    )


if __name__ == "__main__":
    main()
