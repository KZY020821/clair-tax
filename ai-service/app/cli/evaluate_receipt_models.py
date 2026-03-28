from __future__ import annotations

import argparse
import json

from app.core.settings import Settings
from app.services.postprocessing import load_receipt_model_artifact
from app.services.training import evaluate_artifact_on_entries, load_manifest_entries


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Evaluate a trained receipt artifact on a labeled manifest."
    )
    parser.add_argument("--manifest", required=True, help="Path to the labeled manifest.")
    parser.add_argument(
        "--artifact",
        required=True,
        help="Path to the joblib artifact created by train_receipt_models.",
    )
    args = parser.parse_args()

    entries = load_manifest_entries(args.manifest)
    artifact = load_receipt_model_artifact(args.artifact)
    metrics = evaluate_artifact_on_entries(artifact, entries, settings=Settings())
    print(
        json.dumps(
            {
                "artifactVersion": artifact.artifact_version,
                "manifestReceiptCount": len(entries),
                "metrics": metrics,
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
