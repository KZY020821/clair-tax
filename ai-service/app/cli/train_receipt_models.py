from __future__ import annotations

import argparse
import json

from app.core.settings import Settings
from app.services.training import (
    build_training_dataset,
    load_manifest_entries,
    train_receipt_models,
    write_training_tables,
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train receipt amount/date selection models from a labeled manifest."
    )
    parser.add_argument("--manifest", required=True, help="Path to the labeled manifest.")
    parser.add_argument(
        "--output",
        required=True,
        help="Path to write the joblib model artifact.",
    )
    parser.add_argument(
        "--candidate-table-dir",
        help="Optional directory to export tabular candidate training rows.",
    )
    parser.add_argument(
        "--require-beats-baseline",
        action="store_true",
        help="Fail training if holdout metrics do not beat the heuristic baseline.",
    )
    args = parser.parse_args()

    entries = load_manifest_entries(args.manifest)
    result = train_receipt_models(
        entries,
        output_path=args.output,
        settings=Settings(),
        require_beats_baseline=args.require_beats_baseline,
    )
    if args.candidate_table_dir:
        dataset = build_training_dataset(entries)
        result["candidateTables"] = write_training_tables(
            dataset, args.candidate_table_dir
        )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
