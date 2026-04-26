# Agent Prompt: Extend AI Service Receipt Training Pipeline

You are working on the `ai-service/` directory of the **Clair Tax** monorepo — a Malaysian personal tax management system. The AI service is a FastAPI microservice that performs receipt OCR and structured data extraction using EasyOCR.

Your task is to implement **two independent improvements** to the receipt processing and model training pipeline. Implement both features completely, including unit tests.

---

## Codebase Context

The AI service processes receipt images to extract `amount`, `date`, and `merchant_name`. It has two paths:

1. **Live processing** (`app/clients/ocr.py`, `app/services/processing.py`): Already supports PDF via PyMuPDF.
2. **Training pipeline** (`app/cli/prepare_receipt_annotations.py`, `app/services/normalization.py`): The two gaps you are fixing.

The extraction pipeline is:
```
raw image/PDF bytes
  → extract_blocks_from_file() [ocr.py]       # OCR → list[OcrBlock]
  → extract_amount/date/merchant_candidates()  # heuristic scoring → list[Candidate]
  → postprocess()                              # pick best per field
  → ExtractionResult
```

---

## Files You Must Modify

1. `ai-service/app/cli/prepare_receipt_annotations.py`
2. `ai-service/app/services/normalization.py`
3. `ai-service/requirements.txt` — add `word2number`
4. `ai-service/tests/unit/test_normalization.py` — add test cases for both features

---

## Current File Contents

### `ai-service/app/cli/prepare_receipt_annotations.py`
```python
"""
CLI tool to prepare receipt annotations from local images or S3.

Usage:
    python -m app.cli.prepare_receipt_annotations --input-dir ./samples --output ./data/annotations.jsonl
    python -m app.cli.prepare_receipt_annotations --input-dir s3://bucket/prefix --output ./data/annotations.jsonl --source s3
"""

import argparse
import json
import os
import sys
import uuid
from pathlib import Path

import structlog

from app.clients.ocr import extract_blocks
from app.models.training import AnnotatedReceipt, LabeledBlock

logger = structlog.get_logger(__name__)

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp"}


def _process_image(image_bytes: bytes, image_path: str, s3_key: str | None = None) -> AnnotatedReceipt:
    receipt_id = str(uuid.uuid4())
    blocks = extract_blocks(image_bytes, receipt_id=receipt_id)

    labeled_blocks = [
        LabeledBlock(
            text=b.text,
            confidence=b.confidence,
            bbox=b.bbox,
            line_index=b.line_index,
            is_amount=False,
            is_date=False,
            is_merchant=False,
        )
        for b in blocks
    ]

    return AnnotatedReceipt(
        receipt_id=receipt_id,
        s3_key=s3_key,
        image_path=image_path,
        ground_truth_amount=None,
        ground_truth_date=None,
        ground_truth_merchant=None,
        blocks=labeled_blocks,
    )


def _process_local_dir(input_dir: str, output_path: str) -> None:
    input_path = Path(input_dir)
    if not input_path.exists():
        logger.error("input_dir_not_found", input_dir=input_dir)
        sys.exit(1)

    image_files = [
        f for f in input_path.iterdir()
        if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS
    ]

    if not image_files:
        logger.warning("no_images_found", input_dir=input_dir)
        os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
        Path(output_path).touch()
        return

    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    written = 0

    with open(output_path, "w", encoding="utf-8") as f:
        for image_file in sorted(image_files):
            try:
                image_bytes = image_file.read_bytes()
                receipt = _process_image(
                    image_bytes=image_bytes,
                    image_path=str(image_file),
                    s3_key=None,
                )
                f.write(receipt.model_dump_json() + "\n")
                written += 1
                logger.info("annotation_written", image_path=str(image_file), receipt_id=receipt.receipt_id)
            except Exception as exc:
                logger.error("annotation_failed", image_path=str(image_file), detail=str(exc))

    logger.info("prepare_complete", output_path=output_path, written=written, total=len(image_files))


def _process_s3(s3_uri: str, output_path: str) -> None:
    from app.clients.s3_storage import S3StorageClient
    from app.config import get_settings

    settings = get_settings()
    client = S3StorageClient(settings)

    if s3_uri.startswith("s3://"):
        s3_uri = s3_uri[5:]
    parts = s3_uri.split("/", 1)
    bucket = parts[0]
    prefix = parts[1] if len(parts) > 1 else ""

    import boto3

    s3 = boto3.client(
        "s3",
        region_name=settings.AWS_REGION,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
    )

    paginator = s3.get_paginator("list_objects_v2")
    pages = paginator.paginate(Bucket=bucket, Prefix=prefix)

    os.makedirs(os.path.dirname(output_path) if os.path.dirname(output_path) else ".", exist_ok=True)
    written = 0

    with open(output_path, "w", encoding="utf-8") as f:
        for page in pages:
            for obj in page.get("Contents", []):
                key = obj["Key"]
                ext = os.path.splitext(key)[1].lower()
                if ext not in SUPPORTED_EXTENSIONS:
                    continue
                try:
                    image_bytes = client.download_receipt(key)
                    receipt = _process_image(
                        image_bytes=image_bytes,
                        image_path=f"s3://{bucket}/{key}",
                        s3_key=key,
                    )
                    f.write(receipt.model_dump_json() + "\n")
                    written += 1
                    logger.info("annotation_written", s3_key=key, receipt_id=receipt.receipt_id)
                except Exception as exc:
                    logger.error("annotation_failed", s3_key=key, detail=str(exc))

    logger.info("prepare_complete", output_path=output_path, written=written)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare receipt annotations (OCR blocks) for labeling and training."
    )
    parser.add_argument("--input-dir", required=True, help="Local directory or S3 URI of receipt images")
    parser.add_argument("--output", required=True, help="Output JSONL file path")
    parser.add_argument("--source", choices=["local", "s3"], default="local", help="Image source")
    args = parser.parse_args()

    if args.source == "s3":
        _process_s3(args.input_dir, args.output)
    else:
        _process_local_dir(args.input_dir, args.output)


if __name__ == "__main__":
    main()
```

---

### `ai-service/app/clients/ocr.py` — READ ONLY, do not modify
The key function to reuse is `extract_blocks_from_file`. It already handles both image bytes and PDF bytes:
- Detects PDF via magic bytes (`%PDF`)
- Converts each PDF page to PNG via PyMuPDF at 150 DPI
- Runs EasyOCR per page, offsets `line_index` so page 2 blocks follow page 1
- For non-PDF input, falls through to `extract_blocks()` unchanged

```python
def extract_blocks_from_file(file_bytes: bytes, receipt_id: str = "") -> list[OcrBlock]:
    """Accept image bytes (PNG/JPG/JPEG) or PDF bytes and return OCR blocks."""
    if not _is_pdf(file_bytes):
        return extract_blocks(file_bytes, receipt_id)
    try:
        pages = _pdf_to_image_bytes_list(file_bytes)
    except Exception as exc:
        raise OcrExtractionError(receipt_id=receipt_id, cause=exc) from exc
    all_blocks = []
    line_index_offset = 0
    for page_bytes in pages:
        page_blocks = extract_blocks(page_bytes, receipt_id)
        for block in page_blocks:
            all_blocks.append(OcrBlock(
                text=block.text, confidence=block.confidence,
                bbox=block.bbox, line_index=block.line_index + line_index_offset,
            ))
        if page_blocks:
            line_index_offset = max(b.line_index for b in page_blocks) + 1
    return all_blocks
```

---

### `ai-service/app/services/normalization.py` — full content (extend this file)

```python
import re
from dataclasses import dataclass, field
from datetime import datetime

from app.clients.ocr import OcrBlock

TOTAL_KEYWORDS = {
    "TOTAL", "JUMLAH", "AMAUN", "AMOUNT DUE", "JUMLAH KESELURUHAN", "GRAND TOTAL",
    "BALANCE DUE", "TOTAL DUE", "PAYMENT DUE", "DUE NOW", "INVOICE TOTAL",
    "AMOUNT PAYABLE", "TOTAL AMOUNT", "NET TOTAL", "NET AMOUNT",
}
TAX_KEYWORDS = {"SST", "GST", "CUKAI", "DISKAUN", "DISCOUNT", "TAX", "SERVICE CHARGE"}

_CURRENCY_PREFIX_RE = re.compile(r"(?:\$|USD|RM|MYR)\s*[\d]", re.IGNORECASE)

_AMOUNT_PATTERN = re.compile(
    r"(?:(\$|USD|RM|MYR)\s*)?(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?|\d{1,7}\.\d{1,2})",
    re.IGNORECASE,
)

_DATE_PATTERNS = [
    (re.compile(r"\b(\d{2})[/\-](\d{2})[/\-](\d{4})\b"), "%d/%m/%Y"),
    (re.compile(r"\b(\d{4})[/\-](\d{2})[/\-](\d{2})\b"), "%Y-%m-%d"),
    (re.compile(r"\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})\b", re.IGNORECASE), "%d %b %Y"),
    (re.compile(r"\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})\b", re.IGNORECASE), "%b %d %Y"),
]

_DATE_LABEL_RE = re.compile(
    r"\b(DATE|TARIKH|ISSUED?|ISSUE DATE|DATE OF ISSUE|INVOICE DATE|DUE DATE)\b",
    re.IGNORECASE,
)

_COMPANY_SUFFIX_RE = re.compile(
    r"\b(Inc\.?|LLC|Ltd\.?|PBC|Corp\.?|Co\.?|SDN\s*BHD|BHD|PLT|LLP|PTE|PVT)\b",
    re.IGNORECASE,
)

_NON_MERCHANT_PATTERNS = re.compile(
    r"^\s*(?:RM|MYR|TOTAL|JUMLAH|RECEIPT|RESIT|INVOICE|INVOIS|SST|GST|"
    r"CUKAI|THANK YOU|TERIMA KASIH|TEL|FAX|DATE|TARIKH|TIME|MASA|"
    r"CASHIER|ITEM|QTY|QUANTITY|PRICE|HARGA|SUBTOTAL|CASH|CHANGE|BAKI|"
    r"PMB|P\.O\.|PO BOX|NO\.|LOT|LEVEL|FLOOR|UNIT|BILL TO|SHIP TO|FROM)\s*",
    re.IGNORECASE,
)


@dataclass
class Candidate:
    value: str
    raw_text: str
    confidence: float
    heuristic_score: float
    block_index: int
    features: dict = field(default_factory=dict)


def _normalise_amount_str(raw: str) -> str:
    cleaned = re.sub(r"[^\d.]", "", raw.replace(",", ""))
    try:
        return f"{float(cleaned):.2f}"
    except ValueError:
        return cleaned


def _parse_amount(text: str) -> list[tuple[str, float, bool]]:
    results = []
    for m in _AMOUNT_PATTERN.finditer(text):
        currency_group = m.group(1)
        num_group = m.group(2)
        has_prefix = currency_group is not None
        if not has_prefix and "." not in num_group:
            continue
        try:
            val = float(re.sub(r"[^\d.]", "", num_group.replace(",", "")))
            results.append((_normalise_amount_str(num_group), val, has_prefix))
        except ValueError:
            pass
    return results


def _clean_for_date(text: str) -> str:
    return re.sub(r"[–—]", " ", text)


def _parse_date(text: str) -> tuple[str, str] | None:
    cleaned = _clean_for_date(text)
    for pattern, fmt in _DATE_PATTERNS:
        m = pattern.search(cleaned)
        if m:
            groups = m.groups()
            try:
                if fmt == "%d/%m/%Y":
                    parsed = datetime(int(groups[2]), int(groups[1]), int(groups[0]))
                    return parsed.strftime("%Y-%m-%d"), "DD/MM/YYYY"
                elif fmt == "%Y-%m-%d":
                    parsed = datetime(int(groups[0]), int(groups[1]), int(groups[2]))
                    return parsed.strftime("%Y-%m-%d"), "YYYY-MM-DD"
                elif fmt == "%d %b %Y":
                    raw_str = f"{groups[0]} {groups[1]} {groups[2]}"
                    parsed = datetime.strptime(raw_str, "%d %b %Y")
                    return parsed.strftime("%Y-%m-%d"), "DD Mon YYYY"
                elif fmt == "%b %d %Y":
                    raw_str = f"{groups[0]} {groups[1]} {groups[2]}"
                    parsed = datetime.strptime(raw_str, "%b %d %Y")
                    return parsed.strftime("%Y-%m-%d"), "Mon DD YYYY"
            except (ValueError, IndexError):
                continue
    return None


def _block_text_upper(block: OcrBlock) -> str:
    return block.text.upper().strip()


def _is_total_keyword(text: str) -> bool:
    upper = text.upper()
    return any(kw in upper for kw in TOTAL_KEYWORDS)


def _is_tax_keyword(text: str) -> bool:
    upper = text.upper()
    return any(kw in upper for kw in TAX_KEYWORDS)


def _all_amounts_on_receipt(blocks: list[OcrBlock]) -> list[float]:
    amounts = []
    for block in blocks:
        for _, val, _ in _parse_amount(block.text):
            amounts.append(val)
    return amounts


def _receipt_height(blocks: list[OcrBlock]) -> int:
    max_y = 0
    for block in blocks:
        for pt in block.bbox:
            if len(pt) >= 2:
                max_y = max(max_y, pt[1])
    return max_y if max_y > 0 else 1


def _block_cy(block: OcrBlock) -> float:
    if not block.bbox:
        return 0.0
    ys = [pt[1] for pt in block.bbox if len(pt) >= 2]
    return sum(ys) / len(ys) if ys else 0.0


def _is_year_like(val: float) -> bool:
    return val == int(val) and 1900 <= val <= 2099


def extract_amount_candidates(blocks: list[OcrBlock]) -> list[Candidate]:
    if not blocks:
        return []

    all_amounts = _all_amounts_on_receipt(blocks)
    non_year_amounts = [v for v in all_amounts if not _is_year_like(v)]
    max_amount = max(non_year_amounts) if non_year_amounts else 0.0
    receipt_height = _receipt_height(blocks)

    candidates: list[Candidate] = []

    for idx, block in enumerate(blocks):
        parsed = _parse_amount(block.text)
        if not parsed:
            continue

        block_upper = _block_text_upper(block)
        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0

        context_blocks = [b for b in blocks if abs(b.line_index - block.line_index) <= 1]
        context_text = " ".join(_block_text_upper(b) for b in context_blocks)

        for norm_str, val, has_currency_prefix in parsed:
            score = 0.0

            if _is_total_keyword(context_text) or _is_total_keyword(block_upper):
                score += 0.4
            if val == max_amount and val > 0 and not _is_year_like(val):
                score += 0.3
            if relative_y >= 0.60:
                score += 0.2
            if has_currency_prefix:
                score += 0.3
            if _is_tax_keyword(context_text):
                score -= 0.3

            same_line_amounts = []
            for b in blocks:
                if b.line_index == block.line_index:
                    for _, v, _ in _parse_amount(b.text):
                        same_line_amounts.append(v)
            if any(v > val for v in same_line_amounts):
                score -= 0.2

            if _is_year_like(val):
                score -= 0.5

            features = {
                "ocr_confidence": block.confidence,
                "heuristic_score": score,
                "char_count": len(norm_str),
                "digit_ratio": sum(c.isdigit() for c in norm_str) / max(len(norm_str), 1),
                "is_all_caps": block_upper == block.text.upper(),
                "line_index": block.line_index,
                "relative_y_position": relative_y,
                "relative_x_position": (
                    sum(pt[0] for pt in block.bbox) / len(block.bbox) / max(receipt_height, 1)
                    if block.bbox else 0.0
                ),
                "has_total_keyword_nearby": _is_total_keyword(context_text),
                "has_tax_keyword_nearby": _is_tax_keyword(context_text),
                "is_largest_amount": val == max_amount,
                "keyword_distance": min(
                    (abs(idx - i) for i, b in enumerate(blocks) if _is_total_keyword(_block_text_upper(b))),
                    default=99,
                ),
            }

            candidates.append(Candidate(
                value=norm_str, raw_text=block.text, confidence=block.confidence,
                heuristic_score=score, block_index=idx, features=features,
            ))

    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)


def extract_date_candidates(blocks: list[OcrBlock]) -> list[Candidate]:
    if not blocks:
        return []
    receipt_height = _receipt_height(blocks)
    now = datetime.utcnow()
    candidates: list[Candidate] = []
    for idx, block in enumerate(blocks):
        result = _parse_date(block.text)
        if not result:
            continue
        iso_date, fmt_name = result
        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0
        context_blocks = [b for b in blocks if abs(b.line_index - block.line_index) <= 1]
        context_text = " ".join(b.text for b in context_blocks)
        score = 0.0
        if fmt_name in ("DD/MM/YYYY", "YYYY-MM-DD"):
            score += 0.5
        elif fmt_name in ("DD Mon YYYY", "Mon DD YYYY"):
            score += 0.3
        if relative_y <= 0.30:
            score += 0.3
        if _DATE_LABEL_RE.search(context_text):
            score += 0.2
        try:
            parsed_dt = datetime.strptime(iso_date, "%Y-%m-%d")
            if parsed_dt > now:
                score -= 0.2
        except ValueError:
            pass
        features = {
            "ocr_confidence": block.confidence, "heuristic_score": score,
            "char_count": len(iso_date),
            "digit_ratio": sum(c.isdigit() for c in iso_date) / max(len(iso_date), 1),
            "is_all_caps": block.text.upper() == block.text,
            "line_index": block.line_index, "relative_y_position": relative_y,
            "relative_x_position": 0.0, "has_total_keyword_nearby": False,
            "has_tax_keyword_nearby": False, "is_largest_amount": False, "keyword_distance": 99,
        }
        candidates.append(Candidate(
            value=iso_date, raw_text=block.text, confidence=block.confidence,
            heuristic_score=score, block_index=idx, features=features,
        ))
    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)


def extract_merchant_candidates(blocks: list[OcrBlock]) -> list[Candidate]:
    if not blocks:
        return []
    receipt_height = _receipt_height(blocks)
    candidates: list[Candidate] = []
    top_blocks = (
        [b for b in blocks if _block_cy(b) / receipt_height <= 0.35]
        if receipt_height else blocks[:5]
    )
    if not top_blocks:
        top_blocks = blocks[:5]
    for idx, block in enumerate(blocks):
        if block not in top_blocks:
            continue
        text = block.text.strip()
        if len(text) < 3:
            continue
        if _NON_MERCHANT_PATTERNS.match(text):
            continue
        digit_ratio = sum(c.isdigit() for c in text) / max(len(text), 1)
        if digit_ratio > 0.5:
            continue
        if _parse_amount(text) and not re.search(r"[A-Za-z]{3,}", text):
            continue
        if _parse_date(text):
            continue
        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0
        score = block.confidence
        if text.upper() == text and len(text) > 4:
            score += 0.1
        if block.line_index == 0:
            score += 0.2
        elif block.line_index == 1:
            score += 0.1
        if _COMPANY_SUFFIX_RE.search(text):
            score += 0.25
        if len(text) < 5:
            score -= 0.2
        if digit_ratio > 0.3:
            score -= 0.2
        features = {
            "ocr_confidence": block.confidence, "heuristic_score": score,
            "char_count": len(text), "digit_ratio": digit_ratio,
            "is_all_caps": text.upper() == text, "line_index": block.line_index,
            "relative_y_position": relative_y, "relative_x_position": 0.0,
            "has_total_keyword_nearby": False, "has_tax_keyword_nearby": False,
            "is_largest_amount": False, "keyword_distance": 99,
        }
        candidates.append(Candidate(
            value=text, raw_text=text, confidence=block.confidence,
            heuristic_score=score, block_index=idx, features=features,
        ))
    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)
```

---

## Feature 1: PDF Support in the Training Annotation CLI

### Root Cause
`SUPPORTED_EXTENSIONS` in `prepare_receipt_annotations.py` does not include `.pdf`, and `_process_image` calls `extract_blocks()` which only accepts image bytes — not PDF bytes.

### Required Changes

**1. Add `.pdf` to `SUPPORTED_EXTENSIONS`:**
```python
SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp", ".pdf"}
```

**2. Swap the import — use `extract_blocks_from_file` instead of `extract_blocks`:**
```python
from app.clients.ocr import extract_blocks_from_file
```
`extract_blocks_from_file` already handles PDF bytes correctly (see ocr.py above). It auto-detects PDF via magic bytes and falls back to image processing for non-PDF files. No change to the function signature needed.

**3. In `_process_image`, replace the call:**
```python
# before:
blocks = extract_blocks(image_bytes, receipt_id=receipt_id)
# after:
blocks = extract_blocks_from_file(image_bytes, receipt_id=receipt_id)
```

**4. In `_process_local_dir`, switch from `iterdir()` to `rglob("*")`** so nested subdirectories of PDF samples are also scanned:
```python
image_files = [
    f for f in input_path.rglob("*")
    if f.is_file() and f.suffix.lower() in SUPPORTED_EXTENSIONS
]
```

**5. The S3 path** (`_process_s3`) filters by `ext not in SUPPORTED_EXTENSIONS` — since `.pdf` is now in the set, S3 PDFs are automatically included. No other change needed there.

---

## Feature 2: Written-Out Amount Parsing ("SIXTY THREE ONLY" → 63.00)

### Root Cause
`_parse_amount()` uses a regex that requires digits (`\d`). Blocks like `"SIXTY THREE ONLY"` contain no digits, so they produce zero candidates and are silently ignored.

These occur on Malaysian formal receipts, tax invoices, and cheques where the total is written in English words as a fraud-prevention measure.

### Malaysian patterns to handle

| OCR block text | Expected output |
|---|---|
| `"SIXTY THREE ONLY"` | `"63.00"` |
| `"SIXTY THREE AND CENTS TWENTY ONLY"` | `"63.20"` |
| `"RM SIXTY THREE ONLY"` | `"63.00"` |
| `"MYR SIXTY THREE ONLY"` | `"63.00"` |
| `"RINGGIT MALAYSIA SIXTY THREE ONLY"` | `"63.00"` |
| `"RINGGIT MALAYSIA SIXTY THREE AND CENTS TWENTY ONLY"` | `"63.20"` |
| `"ONE HUNDRED AND TWENTY FIVE AND CENTS FIFTY ONLY"` | `"125.50"` |
| `"ZERO AND CENTS FIFTY ONLY"` | `"0.50"` |

### Add `word2number` to requirements

In `ai-service/requirements.txt`, add:
```
word2number
```
Import as: `from word2number import w2n`. `w2n.word_to_num("sixty three")` returns `63`.

### Implement `_parse_word_amount(text)` in `normalization.py`

Add this function. It must **never raise** — always `try/except Exception: return None`.

**Algorithm:**
1. Uppercase and strip `text`.
2. If the text contains any digit character (`re.search(r"\d", text)`), return `None` immediately — those blocks are already handled by `_parse_amount`.
3. Strip currency/noise tokens using regex: `RINGGIT\s*MALAYSIA\b|RINGGIT\b|\bRM\b|\bMYR\b|\bONLY\b` (case-insensitive). Strip resulting whitespace.
4. Guard: check if the cleaned text contains at least one token from the English number-word vocabulary:
   ```python
   _WORD_AMOUNT_VOCAB = {
       "ZERO","ONE","TWO","THREE","FOUR","FIVE","SIX","SEVEN","EIGHT","NINE",
       "TEN","ELEVEN","TWELVE","THIRTEEN","FOURTEEN","FIFTEEN","SIXTEEN",
       "SEVENTEEN","EIGHTEEN","NINETEEN","TWENTY","THIRTY","FORTY","FIFTY",
       "SIXTY","SEVENTY","EIGHTY","NINETY","HUNDRED","THOUSAND","MILLION",
   }
   ```
   If none of these appear as a word in `cleaned.upper()`, return `None`.
5. Split on `AND\s+CENTS` (case-insensitive, max 1 split):
   - 2 parts → `ringgit_part = parts[0]`, `cents_part = parts[1]`
   - 1 part → `ringgit_part = cleaned`, `cents_part = None`
6. Call `w2n.word_to_num(ringgit_part.strip())` → integer ringgit value.
7. If `cents_part`: call `w2n.word_to_num(cents_part.strip())` → cents (0–99).
8. `total = ringgit_value + (cents_value / 100 if cents_part else 0.0)`
9. Return `(f"{total:.2f}", total)`.
10. Entire body is wrapped in `try/except Exception: return None`.

### Integrate into `extract_amount_candidates()`

Inside the main `for idx, block in enumerate(blocks)` loop, after `parsed = _parse_amount(block.text)`, add a word-amount fallback branch **inside** the `if not parsed:` block:

```python
parsed = _parse_amount(block.text)
if not parsed:
    word_result = _parse_word_amount(block.text)
    if word_result:
        norm_str, val = word_result
        block_upper = _block_text_upper(block)
        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0
        context_blocks = [b for b in blocks if abs(b.line_index - block.line_index) <= 1]
        context_text = " ".join(_block_text_upper(b) for b in context_blocks)

        # Written-out amounts are almost always the grand total (legal/formal line)
        score = 0.5  # strong base — word form implies an intentional declared total
        if _is_total_keyword(context_text) or _is_total_keyword(block_upper):
            score += 0.4
        if relative_y >= 0.60:
            score += 0.2
        if _is_tax_keyword(context_text):
            score -= 0.3

        features = {
            "ocr_confidence": block.confidence,
            "heuristic_score": score,
            "char_count": len(norm_str),
            "digit_ratio": 0.0,
            "is_all_caps": block_upper == block.text.upper(),
            "line_index": block.line_index,
            "relative_y_position": relative_y,
            "relative_x_position": (
                sum(pt[0] for pt in block.bbox) / len(block.bbox) / max(receipt_height, 1)
                if block.bbox else 0.0
            ),
            "has_total_keyword_nearby": _is_total_keyword(context_text),
            "has_tax_keyword_nearby": _is_tax_keyword(context_text),
            "is_largest_amount": False,
            "keyword_distance": min(
                (abs(idx - i) for i, b in enumerate(blocks) if _is_total_keyword(_block_text_upper(b))),
                default=99,
            ),
        }
        candidates.append(Candidate(
            value=norm_str, raw_text=block.text, confidence=block.confidence,
            heuristic_score=score, block_index=idx, features=features,
        ))
    continue  # move to next block regardless
```

### Update `_all_amounts_on_receipt()` to include word-form amounts

This is used to compute `max_amount` for the `+0.3 is_largest_amount` bonus. Without this update, a written grand total of 63.00 would not be considered the largest amount, even if all numeric items are smaller:

```python
def _all_amounts_on_receipt(blocks: list[OcrBlock]) -> list[float]:
    amounts = []
    for block in blocks:
        for _, val, _ in _parse_amount(block.text):
            amounts.append(val)
        if not _parse_amount(block.text):
            word_result = _parse_word_amount(block.text)
            if word_result:
                amounts.append(word_result[1])
    return amounts
```

The `if not _parse_amount(block.text)` guard prevents double-counting on numeric blocks (which already contribute via `_parse_amount`). The digit guard inside `_parse_word_amount` provides a second safety layer.

---

## Tests to Add in `ai-service/tests/unit/test_normalization.py`

Add a new test class. Use the existing `make_block()` helper already present in the test file.

```python
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
```

---

## Constraints

1. **Do not modify `ocr.py`** — only consume `extract_blocks_from_file` from it.
2. **`_parse_word_amount` must never raise** — wrap the entire body in `try/except Exception: return None`.
3. **No double-counting** — numeric blocks go through `_parse_amount` only; word blocks go through `_parse_word_amount` only. The digit guard in `_parse_word_amount` enforces this.
4. **English-only word parsing** — Malay number words (ENAM PULUH TIGA) are out of scope.
5. **Preserve all existing behaviour** — only add new code paths; do not refactor existing scoring logic.
6. **Run `ruff check ai-service/app ai-service/tests`** and fix any issues before finishing.
7. **Run `pytest ai-service/tests`** and confirm all existing tests still pass alongside the new ones.

---

## Verification Checklist

- [ ] `SUPPORTED_EXTENSIONS` includes `".pdf"`
- [ ] `_process_image` calls `extract_blocks_from_file`, not `extract_blocks`
- [ ] `_process_local_dir` uses `rglob("*")` so nested folders are scanned
- [ ] `word2number` added to `requirements.txt`
- [ ] `_parse_word_amount(text)` implemented in `normalization.py`, never raises
- [ ] `_WORD_AMOUNT_VOCAB` guard prevents false positives on non-amount text
- [ ] `AND CENTS` split correctly maps ringgit + cents to decimal (e.g., 63.20)
- [ ] `extract_amount_candidates()` calls `_parse_word_amount` for blocks with no numeric match
- [ ] `_all_amounts_on_receipt()` includes word-form amounts for `max_amount` calculation
- [ ] All 11 new `TestWordAmountCandidates` tests pass
- [ ] All pre-existing tests pass
- [ ] Ruff reports no linting errors
