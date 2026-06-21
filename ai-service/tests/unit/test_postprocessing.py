"""Unit tests for postprocessing module."""

import pytest

from app.config import get_settings
from app.models.job import ReceiptJob
from app.services.normalization import Candidate
from app.services.postprocessing import (
    _determine_status,
    _select_heuristic,
    postprocess,
)


def make_candidate(value: str, heuristic_score: float, confidence: float = 0.90, block_index: int = 0) -> Candidate:
    return Candidate(
        value=value,
        raw_text=value,
        confidence=confidence,
        heuristic_score=heuristic_score,
        block_index=block_index,
        features={
            "ocr_confidence": confidence,
            "heuristic_score": heuristic_score,
            "char_count": len(value),
            "digit_ratio": 0.5,
            "is_all_caps": False,
            "line_index": block_index,
            "relative_y_position": 0.5,
            "relative_x_position": 0.5,
            "has_total_keyword_nearby": False,
            "has_tax_keyword_nearby": False,
            "is_largest_amount": False,
            "keyword_distance": 5,
        },
    )


@pytest.fixture
def fake_job() -> ReceiptJob:
    return ReceiptJob(receipt_id="test-123", s3_key="receipts/test.jpg", user_id="user-1")


class TestSelectHeuristic:
    def test_selects_highest_scoring_candidate(self):
        candidates = [
            make_candidate("32.54", heuristic_score=0.9),
            make_candidate("15.90", heuristic_score=0.5),
            make_candidate("8.50", heuristic_score=0.2),
        ]
        # Already sorted descending by caller convention
        result = _select_heuristic(candidates, threshold=0.5)
        assert result is not None
        assert result.value == "32.54"

    def test_rejects_below_threshold(self):
        candidates = [make_candidate("5.00", heuristic_score=0.3)]
        result = _select_heuristic(candidates, threshold=0.5)
        assert result is None

    def test_accepts_at_threshold(self):
        candidates = [make_candidate("5.00", heuristic_score=0.5)]
        result = _select_heuristic(candidates, threshold=0.5)
        assert result is not None

    def test_empty_list_returns_none(self):
        result = _select_heuristic([], threshold=0.5)
        assert result is None


class TestDetermineStatus:
    def test_no_amount_is_invalid(self):
        assert _determine_status(None, "2024-03-15", "MYDIN") == "invalid"

    def test_amount_only_no_date_no_merchant_is_partial(self):
        assert _determine_status("32.54", None, None) == "partial"

    def test_amount_plus_date_is_extracted(self):
        assert _determine_status("32.54", "2024-03-15", None) == "extracted"

    def test_amount_plus_merchant_is_extracted(self):
        assert _determine_status("32.54", None, "MYDIN") == "extracted"

    def test_all_fields_is_extracted(self):
        assert _determine_status("32.54", "2024-03-15", "MYDIN") == "extracted"


class TestPostprocess:
    def test_full_extraction_with_all_fields(self, fake_job, sample_ocr_blocks):
        from app.services.normalization import (
            extract_amount_candidates,
            extract_date_candidates,
            extract_merchant_candidates,
        )

        settings = get_settings()
        amount_cands = extract_amount_candidates(sample_ocr_blocks)
        date_cands = extract_date_candidates(sample_ocr_blocks)
        merchant_cands = extract_merchant_candidates(sample_ocr_blocks)

        result = postprocess(
            blocks=sample_ocr_blocks,
            amount_candidates=amount_cands,
            date_candidates=date_cands,
            merchant_candidates=merchant_cands,
            job=fake_job,
            settings=settings,
        )

        assert result.receipt_id == "test-123"
        assert result.extraction_status in ("extracted", "partial")
        assert result.amount is not None
        assert result.processing_mode == "heuristic"

    def test_no_blocks_produces_no_text_detected(self, fake_job):
        settings = get_settings()
        result = postprocess(
            blocks=[],
            amount_candidates=[],
            date_candidates=[],
            merchant_candidates=[],
            job=fake_job,
            settings=settings,
        )
        # No amount => invalid, but when called from processing.py directly we get no_text_detected
        # postprocess itself: no amount → invalid
        assert result.extraction_status == "invalid"
        assert result.amount is None

    def test_model_fallback_when_artifact_missing(self, fake_job, sample_ocr_blocks, monkeypatch):
        """When TRAINED_RECEIPT_POSTPROCESSOR_ENABLED=True but file missing, falls back silently."""
        monkeypatch.setenv("TRAINED_RECEIPT_POSTPROCESSOR_ENABLED", "true")
        monkeypatch.setenv(
            "TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH",
            "/nonexistent/path/model.joblib",
        )
        get_settings.cache_clear()

        # Reset module-level model cache
        import app.services.postprocessing as pp_module
        pp_module._trained_model = None
        pp_module._model_load_attempted = False

        settings = get_settings()
        from app.services.normalization import (
            extract_amount_candidates,
            extract_date_candidates,
            extract_merchant_candidates,
        )

        amount_cands = extract_amount_candidates(sample_ocr_blocks)
        date_cands = extract_date_candidates(sample_ocr_blocks)
        merchant_cands = extract_merchant_candidates(sample_ocr_blocks)

        # Should not raise — falls back to heuristic
        result = postprocess(
            blocks=sample_ocr_blocks,
            amount_candidates=amount_cands,
            date_candidates=date_cands,
            merchant_candidates=merchant_cands,
            job=fake_job,
            settings=settings,
        )
        assert result is not None
        assert result.processing_mode == "heuristic"

        # Restore
        pp_module._trained_model = None
        pp_module._model_load_attempted = False

    def test_currency_set_when_amount_present(self, fake_job, sample_ocr_blocks):
        from app.services.normalization import extract_amount_candidates

        settings = get_settings()
        amount_cands = extract_amount_candidates(sample_ocr_blocks)

        result = postprocess(
            blocks=sample_ocr_blocks,
            amount_candidates=amount_cands,
            date_candidates=[],
            merchant_candidates=[],
            job=fake_job,
            settings=settings,
        )
        if result.amount is not None:
            assert result.currency == "MYR"

    def test_currency_none_when_amount_absent(self, fake_job):
        settings = get_settings()
        result = postprocess(
            blocks=[],
            amount_candidates=[],
            date_candidates=[],
            merchant_candidates=[],
            job=fake_job,
            settings=settings,
        )
        assert result.currency is None

    def test_block_count_recorded(self, fake_job, sample_ocr_blocks):
        settings = get_settings()
        result = postprocess(
            blocks=sample_ocr_blocks,
            amount_candidates=[],
            date_candidates=[],
            merchant_candidates=[],
            job=fake_job,
            settings=settings,
        )
        assert result.raw_ocr_block_count == len(sample_ocr_blocks)
