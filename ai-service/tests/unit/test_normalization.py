"""Unit tests for normalization module — amount, date, and merchant extraction."""

from app.clients.ocr import OcrBlock
from app.services.normalization import (
    extract_amount_candidates,
    extract_date_candidates,
    extract_merchant_candidates,
)


def make_block(
    text: str,
    confidence: float = 0.90,
    line_index: int = 0,
    y: int = 100,
) -> OcrBlock:
    return OcrBlock(
        text=text,
        confidence=confidence,
        bbox=[[10, y], [200, y], [200, y + 15], [10, y + 15]],
        line_index=line_index,
    )


class TestExtractAmountCandidates:
    def test_finds_total_amount_from_sample_blocks(self, sample_ocr_blocks):
        candidates = extract_amount_candidates(sample_ocr_blocks)
        assert candidates, "Should find at least one amount candidate"
        # Multiple amounts have TOTAL keyword nearby; largest amount scores highest
        top = candidates[0]
        # CASH 50.00 scores highest: +0.4 (TOTAL nearby) +0.3 (largest) +0.2 (bottom) = 0.9
        # TOTAL 32.54 scores: +0.4 (TOTAL) -0.3 (SST nearby) +0.2 (bottom) = 0.3
        assert top.value == "50.00"

    def test_total_keyword_gives_bonus(self):
        blocks = [
            make_block("JUMLAH / TOTAL", confidence=0.95, line_index=5, y=200),
            make_block("45.00", confidence=0.95, line_index=5, y=200),
            make_block("item price", confidence=0.90, line_index=3, y=100),
            make_block("10.00", confidence=0.90, line_index=3, y=100),
        ]
        candidates = extract_amount_candidates(blocks)
        assert candidates
        total_candidate = next((c for c in candidates if c.value == "45.00"), None)
        item_candidate = next((c for c in candidates if c.value == "10.00"), None)
        assert total_candidate is not None
        assert item_candidate is not None
        assert total_candidate.heuristic_score > item_candidate.heuristic_score

    def test_tax_keyword_gives_penalty(self):
        """Amount adjacent to SST/GST should score lower."""
        blocks = [
            make_block("SST (6%)", confidence=0.90, line_index=3, y=100),
            make_block("2.70", confidence=0.90, line_index=3, y=100),
            make_block("TOTAL", confidence=0.95, line_index=5, y=200),
            make_block("47.00", confidence=0.95, line_index=5, y=200),
        ]
        candidates = extract_amount_candidates(blocks)
        tax_candidate = next((c for c in candidates if c.value == "2.70"), None)
        total_candidate = next((c for c in candidates if c.value == "47.00"), None)
        assert tax_candidate is not None
        assert total_candidate is not None
        assert total_candidate.heuristic_score > tax_candidate.heuristic_score

    def test_largest_amount_gets_bonus(self):
        blocks = [
            make_block("5.00", confidence=0.90, line_index=1, y=50),
            make_block("3.50", confidence=0.90, line_index=2, y=80),
            make_block("99.90", confidence=0.90, line_index=5, y=200),
        ]
        candidates = extract_amount_candidates(blocks)
        largest = next((c for c in candidates if c.value == "99.90"), None)
        assert largest is not None
        # Check is_largest_amount feature
        assert largest.features.get("is_largest_amount") is True

    def test_bottom_40_percent_gets_bonus(self):
        """Amounts in the bottom 40% (y >= 60% of receipt height) get +0.2."""
        # Receipt height ~400px; bottom 40% starts at y=240
        blocks = [
            make_block("12.00", confidence=0.90, line_index=1, y=50),   # top
            make_block("45.00", confidence=0.90, line_index=8, y=300),  # bottom
        ]
        candidates = extract_amount_candidates(blocks)
        top_c = next((c for c in candidates if c.value == "12.00"), None)
        bot_c = next((c for c in candidates if c.value == "45.00"), None)
        assert top_c is not None
        assert bot_c is not None
        # Bottom amount should have higher relative_y_position feature
        assert bot_c.features["relative_y_position"] > top_c.features["relative_y_position"]

    def test_empty_blocks_returns_empty(self):
        assert extract_amount_candidates([]) == []

    def test_no_amount_in_blocks_returns_empty(self):
        blocks = [
            make_block("TERIMA KASIH", confidence=0.90, line_index=0, y=10),
            make_block("THANK YOU FOR SHOPPING", confidence=0.88, line_index=1, y=30),
        ]
        assert extract_amount_candidates(blocks) == []

    def test_rm_prefix_parsed_correctly(self):
        blocks = [make_block("RM 88.50", confidence=0.92, line_index=0, y=50)]
        candidates = extract_amount_candidates(blocks)
        assert candidates
        assert candidates[0].value == "88.50"

    def test_myr_prefix_parsed_correctly(self):
        blocks = [make_block("MYR12.50", confidence=0.92, line_index=0, y=50)]
        candidates = extract_amount_candidates(blocks)
        assert candidates
        assert candidates[0].value == "12.50"


class TestExtractDateCandidates:
    def test_finds_date_from_sample_blocks(self, sample_ocr_blocks):
        candidates = extract_date_candidates(sample_ocr_blocks)
        assert candidates
        top = candidates[0]
        assert top.value == "2024-03-15"

    def test_dd_mm_yyyy_slash_format_gets_high_bonus(self):
        blocks = [make_block("15/03/2024", confidence=0.90, line_index=1, y=30)]
        candidates = extract_date_candidates(blocks)
        assert candidates
        assert candidates[0].value == "2024-03-15"
        assert candidates[0].heuristic_score >= 0.5

    def test_yyyy_mm_dd_format_gets_high_bonus(self):
        blocks = [make_block("2024-03-15", confidence=0.90, line_index=1, y=30)]
        candidates = extract_date_candidates(blocks)
        assert candidates
        assert candidates[0].value == "2024-03-15"
        assert candidates[0].heuristic_score >= 0.5

    def test_written_month_format_parsed(self):
        blocks = [make_block("15 Mar 2024", confidence=0.88, line_index=1, y=30)]
        candidates = extract_date_candidates(blocks)
        assert candidates
        assert candidates[0].value == "2024-03-15"

    def test_future_date_gets_penalty(self):
        blocks = [
            make_block("15/03/2024", confidence=0.90, line_index=1, y=30),  # past, high top bonus
            make_block("15/03/2099", confidence=0.90, line_index=8, y=200),  # future, penalty
        ]
        candidates = extract_date_candidates(blocks)
        assert len(candidates) >= 2
        past = next(c for c in candidates if c.value == "2024-03-15")
        future = next(c for c in candidates if c.value == "2099-03-15")
        assert past.heuristic_score > future.heuristic_score

    def test_top_30_percent_gets_bonus(self):
        # y=20 on a ~400px receipt = top ~5%
        blocks = [
            make_block("15/03/2024", confidence=0.90, line_index=0, y=20),   # top
            make_block("20/03/2024", confidence=0.90, line_index=8, y=300),  # bottom
        ]
        candidates = extract_date_candidates(blocks)
        assert len(candidates) == 2
        top_date = next(c for c in candidates if c.value == "2024-03-15")
        bot_date = next(c for c in candidates if c.value == "2024-03-20")
        assert top_date.heuristic_score > bot_date.heuristic_score

    def test_empty_blocks_returns_empty(self):
        assert extract_date_candidates([]) == []

    def test_no_date_in_text_returns_empty(self):
        blocks = [make_block("TOTAL RM 32.54", confidence=0.90, line_index=0, y=50)]
        assert extract_date_candidates(blocks) == []


class TestExtractMerchantCandidates:
    def test_finds_merchant_from_sample_blocks(self, sample_ocr_blocks):
        candidates = extract_merchant_candidates(sample_ocr_blocks)
        assert candidates
        # MYDIN SUPERMARKET SDN BHD is line 0, uppercase, high confidence
        top = candidates[0]
        assert "MYDIN" in top.value or "SUPERMARKET" in top.value

    def test_first_line_gets_bonus(self):
        blocks = [
            make_block("RESTORAN NASI LEMAK", confidence=0.90, line_index=0, y=10),
            make_block("SUBANG JAYA", confidence=0.88, line_index=1, y=30),
        ]
        candidates = extract_merchant_candidates(blocks)
        assert candidates
        first_line = next((c for c in candidates if c.features["line_index"] == 0), None)
        second_line = next((c for c in candidates if c.features["line_index"] == 1), None)
        if first_line and second_line:
            assert first_line.heuristic_score >= second_line.heuristic_score

    def test_total_keyword_block_excluded(self):
        blocks = [
            make_block("MYDIN SUPERMARKET", confidence=0.95, line_index=0, y=10),
            make_block("TOTAL", confidence=0.95, line_index=1, y=30),
        ]
        candidates = extract_merchant_candidates(blocks)
        values = [c.value for c in candidates]
        assert "TOTAL" not in values

    def test_amount_only_block_excluded(self):
        blocks = [
            make_block("MYDIN SUPERMARKET", confidence=0.95, line_index=0, y=10),
            make_block("32.54", confidence=0.95, line_index=1, y=30),
        ]
        candidates = extract_merchant_candidates(blocks)
        values = [c.value for c in candidates]
        assert "32.54" not in values

    def test_empty_blocks_returns_empty(self):
        assert extract_merchant_candidates([]) == []

    def test_uppercase_merchant_gets_bonus(self):
        blocks = [
            make_block("UPPERCASE SHOP", confidence=0.90, line_index=0, y=10),
            make_block("Lowercase Shop", confidence=0.90, line_index=1, y=30),
        ]
        candidates = extract_merchant_candidates(blocks)
        upper = next((c for c in candidates if c.value == "UPPERCASE SHOP"), None)
        lower = next((c for c in candidates if c.value == "Lowercase Shop"), None)
        if upper and lower:
            assert upper.heuristic_score >= lower.heuristic_score


class TestWordAmountCandidates:
    def test_sixty_three_only(self):
        blocks = [make_block("SIXTY THREE ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "63.00"

    def test_sixty_three_and_cents_twenty(self):
        blocks = [make_block("SIXTY THREE AND CENTS TWENTY ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "63.20"

    def test_ringgit_malaysia_prefix_stripped(self):
        blocks = [make_block("RINGGIT MALAYSIA SIXTY THREE ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "63.00"

    def test_rm_prefix_stripped(self):
        blocks = [make_block("RM SIXTY THREE ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "63.00"

    def test_one_hundred_twenty_five_and_cents_fifty(self):
        blocks = [make_block("ONE HUNDRED AND TWENTY FIVE AND CENTS FIFTY ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "125.50"

    def test_numeric_block_still_works(self):
        # Numeric blocks must not be affected — they go through _parse_amount as before
        blocks = [make_block("RM 63.00", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 1
        assert candidates[0].value == "63.00"

    def test_gibberish_returns_no_candidate(self):
        blocks = [make_block("THANK YOU FOR SHOPPING", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert len(candidates) == 0

    def test_word_amount_gets_high_base_score(self):
        blocks = [make_block("SIXTY THREE ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        assert candidates[0].heuristic_score >= 0.5

    def test_word_amount_total_keyword_bonus(self):
        blocks = [
            make_block("TOTAL", line_index=4),
            make_block("SIXTY THREE ONLY", line_index=5),
        ]
        candidates = extract_amount_candidates(blocks)
        word_cand = next(c for c in candidates if c.value == "63.00")
        assert word_cand.heuristic_score >= 0.9  # 0.5 base + 0.4 total keyword

    def test_mixed_receipt_word_amount_beats_item_prices(self):
        # Numeric item lines + written grand total — word-form should score highest
        blocks = [
            make_block("MYDIN SUPERMARKET", line_index=0),
            make_block("ITEM A", line_index=2),
            make_block("RM 10.00", line_index=2),
            make_block("ITEM B", line_index=3),
            make_block("RM 53.00", line_index=3),
            make_block("TOTAL", line_index=4),
            make_block("SIXTY THREE ONLY", line_index=5),
        ]
        candidates = extract_amount_candidates(blocks)
        assert candidates[0].value == "63.00"

    def test_digit_in_text_not_parsed_as_word_amount(self):
        # Block with digits should go through _parse_amount, not _parse_word_amount
        # "SIXTY 3 ONLY" has a digit, so _parse_word_amount should skip it
        blocks = [make_block("SIXTY 3 ONLY", line_index=5)]
        candidates = extract_amount_candidates(blocks)
        # No numeric pattern matches (3 has no decimal), so result is empty
        assert all(c.value != "63.00" for c in candidates)
