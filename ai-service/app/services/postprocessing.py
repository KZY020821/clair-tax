import traceback

import structlog

from app.clients.ocr import OcrBlock
from app.config import Settings
from app.models.extraction import ExtractionResult
from app.models.job import ReceiptJob
from app.services.normalization import Candidate

logger = structlog.get_logger(__name__)

# Module-level cached trained model pipeline
_trained_model = None
_model_load_attempted = False


def _load_model(artifact_path: str):
    global _trained_model, _model_load_attempted
    if _model_load_attempted:
        return _trained_model
    _model_load_attempted = True
    try:
        import joblib

        _trained_model = joblib.load(artifact_path)
        logger.info("postprocessor_model_loaded", artifact_path=artifact_path)
    except FileNotFoundError:
        logger.warning(
            "postprocessor_model_missing",
            artifact_path=artifact_path,
            detail="Falling back to heuristic mode",
        )
        _trained_model = None
    except Exception as exc:
        logger.warning(
            "postprocessor_model_corrupt",
            artifact_path=artifact_path,
            detail=str(exc),
        )
        _trained_model = None
    return _trained_model


def _select_heuristic(candidates: list[Candidate], threshold: float) -> Candidate | None:
    if not candidates:
        return None
    best = candidates[0]  # already sorted by heuristic_score descending
    if best.heuristic_score < threshold:
        return None
    return best


def _select_model(candidates: list[Candidate], model, threshold: float) -> Candidate | None:
    """Use trained model to select candidate. Falls back to heuristic on error."""
    import numpy as np

    if not candidates:
        return None

    try:
        feature_keys = [
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
        X = np.array(
            [[float(c.features.get(k, 0)) for k in feature_keys] for c in candidates]
        )
        proba = model.predict_proba(X)
        # Class 1 = positive (correct candidate)
        positive_class_idx = list(model.classes_).index(1) if hasattr(model, "classes_") else 1
        scores = proba[:, positive_class_idx]
        best_idx = int(np.argmax(scores))
        best_score = float(scores[best_idx])
        if best_score < threshold:
            return None
        best = candidates[best_idx]
        # Override heuristic_score with model probability for downstream use
        best.features["model_score"] = best_score
        return best
    except Exception as exc:
        logger.error(
            "postprocessor_model_inference_failed",
            detail=str(exc),
            traceback=traceback.format_exc(),
        )
        # Fall back to heuristic
        return _select_heuristic(candidates, threshold)


def _determine_status(
    amount: str | None,
    date: str | None,
    merchant: str | None,
) -> str:
    if amount is None:
        return "invalid"
    if date is None and merchant is None:
        return "partial"
    return "extracted"


def postprocess(
    blocks: list[OcrBlock],
    amount_candidates: list[Candidate],
    date_candidates: list[Candidate],
    merchant_candidates: list[Candidate],
    job: ReceiptJob,
    settings: Settings,
) -> ExtractionResult:
    use_model = settings.TRAINED_RECEIPT_POSTPROCESSOR_ENABLED
    processing_mode = "heuristic"

    model = None
    if use_model:
        model = _load_model(settings.TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH)
        if model is not None:
            processing_mode = "model"
        else:
            # Silently fall back
            use_model = False

    def select(candidates: list[Candidate], threshold: float) -> Candidate | None:
        if use_model and model is not None:
            return _select_model(candidates, model, threshold)
        return _select_heuristic(candidates, threshold)

    best_amount = select(amount_candidates, settings.RECEIPT_AMOUNT_SELECTION_THRESHOLD)
    best_date = select(date_candidates, settings.RECEIPT_DATE_SELECTION_THRESHOLD)
    best_merchant = select(merchant_candidates, settings.RECEIPT_VALIDITY_THRESHOLD)

    amount_val = best_amount.value if best_amount else None
    date_val = best_date.value if best_date else None
    merchant_val = best_merchant.value if best_merchant else None

    status = _determine_status(amount_val, date_val, merchant_val)

    return ExtractionResult(
        receipt_id=job.receipt_id,
        extraction_status=status,
        amount=amount_val,
        currency=job.currency if amount_val else None,
        date=date_val,
        merchant_name=merchant_val,
        amount_confidence=best_amount.confidence if best_amount else None,
        date_confidence=best_date.confidence if best_date else None,
        merchant_confidence=best_merchant.confidence if best_merchant else None,
        raw_ocr_block_count=len(blocks),
        processing_mode=processing_mode,
        error_detail=None,
    )
