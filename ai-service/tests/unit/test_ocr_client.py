"""Unit tests for OCR client — block sorting, line_index bucketing, image preprocessing."""

import io
from unittest.mock import MagicMock, patch

import pytest
from PIL import Image

from app.clients.ocr import OcrBlock, OcrExtractionError, _assign_line_indices, extract_blocks


class TestOcrBlockSorting:
    def test_blocks_sorted_top_to_bottom(self):
        """Blocks lower on the receipt (higher y) should appear later."""
        raw = [
            ([[10, 100], [100, 100], [100, 115], [10, 115]], "TOTAL", 0.9),
            ([[10, 20], [100, 20], [100, 35], [10, 35]], "MERCHANT", 0.95),
            ([[10, 60], [100, 60], [100, 75], [10, 75]], "DATE", 0.88),
        ]
        blocks = _assign_line_indices(raw)
        texts = [b.text for b in blocks]
        assert texts.index("MERCHANT") < texts.index("DATE") < texts.index("TOTAL")

    def test_blocks_on_same_line_sorted_left_to_right(self):
        """On the same horizontal line, blocks should be left-to-right."""
        raw = [
            ([[200, 50], [300, 50], [300, 65], [200, 65]], "AMOUNT", 0.9),
            ([[10, 50], [100, 50], [100, 65], [10, 65]], "LABEL", 0.88),
        ]
        blocks = _assign_line_indices(raw)
        texts = [b.text for b in blocks]
        assert texts.index("LABEL") < texts.index("AMOUNT")

    def test_empty_raw_returns_empty(self):
        assert _assign_line_indices([]) == []


class TestLineIndexBucketing:
    def test_blocks_within_10px_get_same_line_index(self):
        """Two blocks whose vertical centres are <=10px apart share a line_index."""
        raw = [
            ([[10, 50], [100, 50], [100, 60], [10, 60]], "LEFT", 0.9),   # cy=55
            ([[110, 53], [200, 53], [200, 63], [110, 63]], "RIGHT", 0.9),  # cy=58, diff=3 → same line
        ]
        blocks = _assign_line_indices(raw)
        assert len(blocks) == 2
        assert blocks[0].line_index == blocks[1].line_index

    def test_blocks_more_than_10px_apart_get_different_line_indices(self):
        """Two blocks with >10px vertical centre gap get different line indices."""
        raw = [
            ([[10, 50], [100, 50], [100, 60], [10, 60]], "LINE1", 0.9),   # cy=55
            ([[10, 80], [100, 80], [100, 90], [10, 90]], "LINE2", 0.9),   # cy=85, diff=30
        ]
        blocks = _assign_line_indices(raw)
        assert len(blocks) == 2
        assert blocks[0].line_index != blocks[1].line_index

    def test_line_indices_are_sequential(self):
        """Line indices should increment 0, 1, 2, ... with no gaps."""
        raw = [
            ([[10, 10], [100, 10], [100, 20], [10, 20]], "A", 0.9),   # cy=15
            ([[10, 40], [100, 40], [100, 50], [10, 50]], "B", 0.9),   # cy=45
            ([[10, 70], [100, 70], [100, 80], [10, 80]], "C", 0.9),   # cy=75
        ]
        blocks = _assign_line_indices(raw)
        indices = [b.line_index for b in blocks]
        assert indices == sorted(set(indices))
        assert indices[0] == 0


class TestImagePreprocessing:
    def test_extract_blocks_converts_to_grayscale(self):
        """extract_blocks should work with a colour image input."""
        img = Image.new("RGB", (800, 200), color=(255, 240, 200))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        mock_reader = MagicMock()
        mock_reader.readtext.return_value = [
            ([[10, 10], [100, 10], [100, 25], [10, 25]], "TEST", 0.9)
        ]

        with patch("app.clients.ocr.get_ocr_reader", return_value=mock_reader):
            blocks = extract_blocks(image_bytes, receipt_id="test")

        assert isinstance(blocks, list)
        assert mock_reader.readtext.called

    def test_small_image_resized_to_1000px_wide(self):
        """Images narrower than 1000px should be upscaled proportionally."""
        img = Image.new("RGB", (400, 200), color=(255, 255, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        captured_images = []

        def capture_readtext(img_bytes):
            pil = Image.open(io.BytesIO(img_bytes))
            captured_images.append(pil.size)
            return []

        mock_reader = MagicMock()
        mock_reader.readtext.side_effect = capture_readtext

        with patch("app.clients.ocr.get_ocr_reader", return_value=mock_reader):
            extract_blocks(image_bytes, receipt_id="test")

        assert captured_images, "readtext should have been called"
        width, height = captured_images[0]
        assert width == 1000
        # Height should be proportionally scaled (400 → 1000 = 2.5x, so 200 → 500)
        assert height == 500

    def test_wide_image_not_resized(self):
        """Images 1000px or wider should not be resized."""
        img = Image.new("RGB", (1200, 600), color=(255, 255, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        captured_images = []

        def capture_readtext(img_bytes):
            pil = Image.open(io.BytesIO(img_bytes))
            captured_images.append(pil.size)
            return []

        mock_reader = MagicMock()
        mock_reader.readtext.side_effect = capture_readtext

        with patch("app.clients.ocr.get_ocr_reader", return_value=mock_reader):
            extract_blocks(image_bytes, receipt_id="test")

        assert captured_images
        width, _ = captured_images[0]
        assert width == 1200

    def test_easyocr_exception_wrapped_as_ocr_extraction_error(self):
        """Any EasyOCR failure should be wrapped as OcrExtractionError."""
        img = Image.new("RGB", (800, 200), color=(255, 255, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        image_bytes = buf.getvalue()

        mock_reader = MagicMock()
        mock_reader.readtext.side_effect = RuntimeError("CUDA out of memory")

        with patch("app.clients.ocr.get_ocr_reader", return_value=mock_reader):
            with pytest.raises(OcrExtractionError) as exc_info:
                extract_blocks(image_bytes, receipt_id="receipt-xyz")

        assert exc_info.value.receipt_id == "receipt-xyz"
        assert isinstance(exc_info.value.cause, RuntimeError)

    def test_returns_ocr_block_dataclasses(self):
        mock_reader = MagicMock()
        mock_reader.readtext.return_value = [
            ([[10, 10], [100, 10], [100, 25], [10, 25]], "HELLO", 0.92)
        ]
        img = Image.new("RGB", (1100, 200), color=(255, 255, 255))
        buf = io.BytesIO()
        img.save(buf, format="PNG")

        with patch("app.clients.ocr.get_ocr_reader", return_value=mock_reader):
            blocks = extract_blocks(buf.getvalue(), receipt_id="test")

        assert len(blocks) == 1
        block = blocks[0]
        assert isinstance(block, OcrBlock)
        assert block.text == "HELLO"
        assert block.confidence == pytest.approx(0.92)
        assert block.line_index == 0


class TestOcrReaderCaching:
    def test_get_ocr_reader_cached_per_process(self):
        """get_ocr_reader should return the same object on repeated calls."""
        import app.clients.ocr as ocr_module

        mock_reader = MagicMock()
        ocr_module._ocr_reader = mock_reader

        from app.clients.ocr import get_ocr_reader

        r1 = get_ocr_reader()
        r2 = get_ocr_reader()
        assert r1 is r2
        assert r1 is mock_reader

        # Clean up
        ocr_module._ocr_reader = None
