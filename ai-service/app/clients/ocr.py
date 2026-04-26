import io
import time
from dataclasses import dataclass

import structlog
from PIL import Image, ImageFilter

from app.config import get_settings

logger = structlog.get_logger(__name__)

# Module-level cached EasyOCR reader — initialised exactly once per process
_ocr_reader = None


class OcrExtractionError(Exception):
    def __init__(self, receipt_id: str, cause: Exception):
        self.receipt_id = receipt_id
        self.cause = cause
        super().__init__(f"OCR failed for receipt {receipt_id}: {cause}")


@dataclass
class OcrBlock:
    text: str
    confidence: float
    bbox: list[list[int]]
    line_index: int


def get_ocr_reader():
    global _ocr_reader
    if _ocr_reader is None:
        import easyocr

        settings = get_settings()
        _ocr_reader = easyocr.Reader(
            ["en"],
            gpu=settings.EASYOCR_GPU,
            model_storage_directory=settings.EASYOCR_MODEL_DIR,
            download_enabled=True,
            verbose=False,
        )
    return _ocr_reader


def _bbox_centre(bbox: list) -> tuple[float, float]:
    """Return (cx, cy) of an EasyOCR bounding box [[x0,y0],[x1,y1],[x2,y2],[x3,y3]]."""
    xs = [p[0] for p in bbox]
    ys = [p[1] for p in bbox]
    return sum(xs) / len(xs), sum(ys) / len(ys)


def _assign_line_indices(raw_results: list) -> list[OcrBlock]:
    """
    Sort EasyOCR results top-to-bottom then left-to-right.
    Bucket blocks whose vertical centres are within 10px into the same line_index.
    """
    if not raw_results:
        return []

    # raw_results: list of (bbox, text, confidence)
    centres = [_bbox_centre(r[0]) for r in raw_results]
    # Sort by cy first, then cx
    indexed = sorted(enumerate(raw_results), key=lambda x: (centres[x[0]][1], centres[x[0]][0]))

    blocks: list[OcrBlock] = []
    current_line = 0
    last_cy: float | None = None

    for orig_idx, raw in indexed:
        bbox, text, confidence = raw
        cx, cy = centres[orig_idx]
        if last_cy is None or abs(cy - last_cy) > 10:
            if last_cy is not None:
                current_line += 1
            last_cy = cy

        int_bbox = [[int(pt[0]), int(pt[1])] for pt in bbox]
        blocks.append(
            OcrBlock(
                text=str(text).strip(),
                confidence=float(confidence),
                bbox=int_bbox,
                line_index=current_line,
            )
        )

    return blocks


def _is_pdf(file_bytes: bytes) -> bool:
    """Detect PDF by magic bytes header."""
    return file_bytes[:4] == b"%PDF"


def _pdf_to_image_bytes_list(pdf_bytes: bytes, dpi: int = 150) -> list[bytes]:
    """
    Convert every page of a PDF to greyscale PNG bytes using PyMuPDF.
    150 DPI yields ~1240px wide for A4 — sufficient for EasyOCR accuracy.
    Raises ValueError if the PDF has no pages or cannot be opened.
    """
    import fitz  # PyMuPDF — lazy import to avoid startup cost when not needed

    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    if doc.page_count == 0:
        raise ValueError("PDF has no pages")
    mat = fitz.Matrix(dpi / 72, dpi / 72)  # 72 is PDF's native DPI
    pages_bytes = []
    for page in doc:
        pix = page.get_pixmap(matrix=mat, colorspace=fitz.csGRAY)
        pages_bytes.append(pix.tobytes("png"))
    return pages_bytes


def extract_blocks_from_file(file_bytes: bytes, receipt_id: str = "") -> list[OcrBlock]:
    """
    Accept image bytes (PNG/JPG/JPEG) or PDF bytes and return OCR blocks.

    Images are passed directly to extract_blocks(). PDFs are converted page
    by page; blocks from each page are merged with line_index values offset
    so that page 2 blocks always follow page 1 blocks.

    Raises OcrExtractionError on any failure.
    """
    if not _is_pdf(file_bytes):
        return extract_blocks(file_bytes, receipt_id)

    try:
        pages = _pdf_to_image_bytes_list(file_bytes)
    except Exception as exc:
        raise OcrExtractionError(receipt_id=receipt_id, cause=exc) from exc

    all_blocks: list[OcrBlock] = []
    line_index_offset = 0
    for page_bytes in pages:
        page_blocks = extract_blocks(page_bytes, receipt_id)
        for block in page_blocks:
            all_blocks.append(
                OcrBlock(
                    text=block.text,
                    confidence=block.confidence,
                    bbox=block.bbox,
                    line_index=block.line_index + line_index_offset,
                )
            )
        if page_blocks:
            line_index_offset = max(b.line_index for b in page_blocks) + 1
    return all_blocks


def extract_blocks(image_bytes: bytes, receipt_id: str = "") -> list[OcrBlock]:
    start = time.monotonic()
    try:
        image = Image.open(io.BytesIO(image_bytes)).convert("L")  # grayscale

        if image.width < 1000:
            ratio = 1000 / image.width
            new_height = int(image.height * ratio)
            image = image.resize((1000, new_height), Image.LANCZOS)

        image = image.filter(ImageFilter.SHARPEN)

        buf = io.BytesIO()
        image.save(buf, format="PNG")
        buf.seek(0)

        reader = get_ocr_reader()
        raw_results = reader.readtext(buf.read())

        blocks = _assign_line_indices(raw_results)

        latency_ms = int((time.monotonic() - start) * 1000)
        logger.debug(
            "ocr_extraction_complete",
            receipt_id=receipt_id,
            block_count=len(blocks),
            latency_ms=latency_ms,
        )
        return blocks

    except Exception as exc:
        raise OcrExtractionError(receipt_id=receipt_id, cause=exc) from exc
