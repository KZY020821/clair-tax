from __future__ import annotations

import argparse
import json

from app.services.training import scaffold_annotation_manifest


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Scaffold a receipt annotation manifest from a directory of files."
    )
    parser.add_argument("--input-dir", required=True, help="Directory containing receipt files.")
    parser.add_argument(
        "--output",
        required=True,
        help="Path to write the JSONL annotation manifest.",
    )
    parser.add_argument(
        "--annotation-source",
        default="sample-pack",
        help="Annotation source label stored in each manifest row.",
    )
    args = parser.parse_args()

    receipt_count = scaffold_annotation_manifest(
        args.input_dir,
        args.output,
        annotation_source=args.annotation_source,
    )
    print(json.dumps({"output": args.output, "receiptCount": receipt_count}, indent=2))


if __name__ == "__main__":
    main()
