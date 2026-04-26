import os
from typing import Any

import joblib
import numpy as np
import structlog
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app.models.training import AnnotatedReceipt
from app.services.normalization import Candidate

logger = structlog.get_logger(__name__)

FEATURE_KEYS = [
    "ocr_confidence",
    "heuristic_score",
    "char_count",
    "digit_ratio",
    "is_all_caps",
    "line_index",
    "relative_y_position",
    "relative_x_position",
    "has_total_keyword_nearby",
    "has_tax_keyword_nearby",
    "is_largest_amount",
    "keyword_distance",
]


def build_features(candidate: Candidate, all_candidates: list[Candidate]) -> dict:
    """Build feature dict for a single candidate given its peers."""
    features = dict(candidate.features)

    # Ensure all expected keys are present with sane defaults
    for key in FEATURE_KEYS:
        if key not in features:
            features[key] = 0

    features["ocr_confidence"] = float(candidate.confidence)
    features["heuristic_score"] = float(candidate.heuristic_score)
    features["char_count"] = len(candidate.value)
    features["digit_ratio"] = (
        sum(c.isdigit() for c in candidate.value) / max(len(candidate.value), 1)
    )
    features["is_all_caps"] = float(candidate.value.upper() == candidate.value)
    features["line_index"] = candidate.block_index

    return features


def _features_to_vector(features: dict) -> list[float]:
    return [float(features.get(k, 0)) for k in FEATURE_KEYS]


def _build_training_samples(
    annotated_receipts: list[AnnotatedReceipt],
    field: str,
) -> tuple[list[list[float]], list[int]]:
    """
    Build (X, y) training arrays for a given field ('amount', 'date', 'merchant').
    Each annotated block is a sample; label=1 if the block matches ground truth.
    """
    from app.clients.ocr import OcrBlock
    from app.services.normalization import (
        extract_amount_candidates,
        extract_date_candidates,
        extract_merchant_candidates,
    )

    X: list[list[float]] = []
    y: list[int] = []

    for receipt in annotated_receipts:
        ground_truth = getattr(receipt, f"ground_truth_{field}", None)

        # Reconstruct OcrBlock list from labeled blocks
        ocr_blocks = [
            OcrBlock(
                text=b.text,
                confidence=b.confidence,
                bbox=b.bbox,
                line_index=b.line_index,
            )
            for b in receipt.blocks
        ]

        if field == "amount":
            candidates = extract_amount_candidates(ocr_blocks)
        elif field == "date":
            candidates = extract_date_candidates(ocr_blocks)
        elif field == "merchant":
            candidates = extract_merchant_candidates(ocr_blocks)
        else:
            continue

        for candidate in candidates:
            feats = build_features(candidate, candidates)
            vec = _features_to_vector(feats)
            label = 1 if (ground_truth and candidate.value == ground_truth) else 0
            X.append(vec)
            y.append(label)

    return X, y


def _build_pipeline() -> Pipeline:
    return Pipeline(
        [
            ("scaler", StandardScaler()),
            (
                "classifier",
                CalibratedClassifierCV(
                    RandomForestClassifier(n_estimators=100, random_state=42),
                    method="isotonic",
                    cv=5,
                ),
            ),
        ]
    )


def train(annotated_receipts: list[AnnotatedReceipt], output_path: str) -> None:
    """
    Train separate pipelines for amount, date, and merchant fields.
    Saves a dict of pipelines to output_path via joblib.
    """
    logger.info("training_started", n_receipts=len(annotated_receipts))

    models: dict[str, Any] = {}

    for field in ("amount", "date", "merchant"):
        X, y = _build_training_samples(annotated_receipts, field)
        if not X:
            logger.warning("training_no_samples", field=field)
            continue

        X_arr = np.array(X)
        y_arr = np.array(y)

        if len(set(y_arr)) < 2:
            logger.warning("training_single_class", field=field, unique_classes=list(set(y_arr)))
            continue

        pipeline = _build_pipeline()
        pipeline.fit(X_arr, y_arr)
        models[field] = pipeline
        logger.info(
            "training_field_complete",
            field=field,
            n_samples=len(X_arr),
            positive_rate=float(y_arr.mean()),
        )

    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    joblib.dump(models, output_path)
    logger.info("training_complete", output_path=output_path, fields=list(models.keys()))


def evaluate(annotated_receipts: list[AnnotatedReceipt], artifact_path: str) -> dict:
    """
    Evaluate a trained model against labeled data.
    Returns per-field precision, recall, F1 and overall metrics.
    """
    from sklearn.metrics import precision_recall_fscore_support

    models: dict[str, Any] = joblib.load(artifact_path)
    results: dict[str, dict] = {}

    for field in ("amount", "date", "merchant"):
        if field not in models:
            results[field] = {"precision": 0.0, "recall": 0.0, "f1": 0.0}
            continue

        X, y = _build_training_samples(annotated_receipts, field)
        if not X:
            results[field] = {"precision": 0.0, "recall": 0.0, "f1": 0.0}
            continue

        X_arr = np.array(X)
        y_arr = np.array(y)

        model = models[field]
        y_pred = model.predict(X_arr)
        precision, recall, f1, _ = precision_recall_fscore_support(
            y_arr, y_pred, average="binary", zero_division=0
        )
        results[field] = {
            "precision": float(precision),
            "recall": float(recall),
            "f1": float(f1),
        }

    # Overall macro average
    all_precisions = [v["precision"] for v in results.values()]
    all_recalls = [v["recall"] for v in results.values()]
    all_f1s = [v["f1"] for v in results.values()]
    results["overall"] = {
        "precision": float(np.mean(all_precisions)),
        "recall": float(np.mean(all_recalls)),
        "f1": float(np.mean(all_f1s)),
    }

    return results
