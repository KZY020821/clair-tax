import os
from dataclasses import dataclass

import structlog

logger = structlog.get_logger(__name__)

_pipeline = None  # module-level singleton

_CANDIDATE_LABELS = [
    "tax relief inquiry",
    "receipt management",
    "profile update",
    "year summary",
    "general question",
]

_TAX_INTENTS = frozenset(
    {"tax relief inquiry", "receipt management", "profile update", "year summary"}
)

_LABEL_TO_INTENT = {
    "tax relief inquiry": "relief_inquiry",
    "receipt management": "receipt_management",
    "profile update": "profile_update",
    "year summary": "year_summary",
    "general question": "general",
}


@dataclass
class ClassificationResult:
    intent: str
    confidence: float


def get_classifier_pipeline():
    """Lazy singleton. Raises on failure; callers must handle."""
    global _pipeline
    if _pipeline is None:
        from app.config import get_settings

        settings = get_settings()
        os.environ.setdefault("HF_HOME", settings.HF_HOME)
        from transformers import pipeline as hf_pipeline

        _pipeline = hf_pipeline(
            "zero-shot-classification",
            model=settings.INTENT_CLASSIFIER_MODEL,
        )
        logger.info("intent_classifier_loaded", model=settings.INTENT_CLASSIFIER_MODEL)
    return _pipeline


def prewarm_intent_classifier() -> None:
    """Called from startup_event. Logs a warning on failure, does not raise."""
    try:
        get_classifier_pipeline()
        logger.info("intent_classifier_prewarm_complete")
    except Exception as exc:
        logger.warning("intent_classifier_prewarm_failed", detail=str(exc))


def classify_intent(text: str) -> ClassificationResult:
    """
    Returns ClassificationResult. Falls back to intent="general", confidence=0.0
    if the classifier is unavailable or disabled (fail-open).
    """
    try:
        from app.config import get_settings

        settings = get_settings()
        if not settings.INTENT_CLASSIFIER_ENABLED:
            return ClassificationResult(intent="general", confidence=0.0)

        clf = get_classifier_pipeline()
        result = clf(text, candidate_labels=_CANDIDATE_LABELS, multi_label=False)
        top_label: str = result["labels"][0]
        top_score: float = result["scores"][0]
        intent = _LABEL_TO_INTENT.get(top_label, "general")
        return ClassificationResult(intent=intent, confidence=top_score)
    except Exception as exc:
        logger.warning("intent_classification_failed", detail=str(exc))
        return ClassificationResult(intent="general", confidence=0.0)
