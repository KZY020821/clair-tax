import re
from dataclasses import dataclass, field
from datetime import datetime

from word2number import w2n

from app.clients.ocr import OcrBlock

# ---------------------------------------------------------------------------
# Keyword sets
# ---------------------------------------------------------------------------
TOTAL_KEYWORDS = {
    "TOTAL", "JUMLAH", "AMAUN", "AMOUNT DUE", "JUMLAH KESELURUHAN", "GRAND TOTAL",
    "BALANCE DUE", "TOTAL DUE", "PAYMENT DUE", "DUE NOW", "INVOICE TOTAL",
    "AMOUNT PAYABLE", "TOTAL AMOUNT", "NET TOTAL", "NET AMOUNT",
}
TAX_KEYWORDS = {"SST", "GST", "CUKAI", "DISKAUN", "DISCOUNT", "TAX", "SERVICE CHARGE"}

# Currency prefixes — used both in regex and in raw-text detection
_CURRENCY_PREFIX_RE = re.compile(r"(?:\$|USD|RM|MYR)\s*[\d]", re.IGNORECASE)

# Amount pattern: optional currency prefix, then the numeric part.
# Requires a decimal point when no currency prefix is present (bare integers
# like "2027" would match otherwise and get confused with years).
_AMOUNT_PATTERN = re.compile(
    r"(?:(\$|USD|RM|MYR)\s*)?(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?|\d{1,7}\.\d{1,2})",
    re.IGNORECASE,
)

# Date patterns — ordered from most to least specific
_DATE_PATTERNS = [
    # DD/MM/YYYY or DD-MM-YYYY
    (re.compile(r"\b(\d{2})[/\-](\d{2})[/\-](\d{4})\b"), "%d/%m/%Y"),
    # YYYY-MM-DD
    (re.compile(r"\b(\d{4})[/\-](\d{2})[/\-](\d{2})\b"), "%Y-%m-%d"),
    # DD Mon(th) YYYY
    (
        re.compile(
            r"\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*"
            r"\s+(\d{4})\b",
            re.IGNORECASE,
        ),
        "%d %b %Y",
    ),
    # Mon(th) DD, YYYY  (US invoice format: "April 17, 2026")
    (
        re.compile(
            r"\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*"
            r"\s+(\d{1,2}),?\s+(\d{4})\b",
            re.IGNORECASE,
        ),
        "%b %d %Y",
    ),
]

# Keyword that signals a date label nearby (boosts date candidate score)
_DATE_LABEL_RE = re.compile(
    r"\b(DATE|TARIKH|ISSUED?|ISSUE DATE|DATE OF ISSUE|INVOICE DATE|DUE DATE)\b",
    re.IGNORECASE,
)

# Company-name suffixes that strongly suggest a merchant name
_COMPANY_SUFFIX_RE = re.compile(
    r"\b(Inc\.?|LLC|Ltd\.?|PBC|Corp\.?|Co\.?|SDN\s*BHD|BHD|PLT|LLP|PTE|PVT)\b",
    re.IGNORECASE,
)

# Non-merchant line starters (expanded to include address/postal indicators)
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


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _normalise_amount_str(raw: str) -> str:
    """Remove currency prefix and thousands separators, normalise to X.XX."""
    cleaned = re.sub(r"[^\d.]", "", raw.replace(",", ""))
    try:
        return f"{float(cleaned):.2f}"
    except ValueError:
        return cleaned


def _parse_amount(text: str) -> list[tuple[str, float, bool]]:
    """
    Return list of (normalised_amount_str, raw_float, has_currency_prefix).
    has_currency_prefix is True when $, USD, RM, or MYR precedes the number.
    """
    results = []
    for m in _AMOUNT_PATTERN.finditer(text):
        currency_group = m.group(1)  # None when no prefix
        num_group = m.group(2)
        has_prefix = currency_group is not None
        # Require decimal point when there is no currency prefix to avoid
        # matching bare integers (years, reference numbers, quantities).
        if not has_prefix and "." not in num_group:
            continue
        try:
            val = float(re.sub(r"[^\d.]", "", num_group.replace(",", "")))
            results.append((_normalise_amount_str(num_group), val, has_prefix))
        except ValueError:
            pass
    return results


def _clean_for_date(text: str) -> str:
    """Strip en-dash/em-dash ranges so only the first date in a range is parsed."""
    # "April 17, 2026–April 17, 2027" → "April 17, 2026 April 17, 2027"
    return re.sub(r"[–—]", " ", text)


def _parse_date(text: str) -> tuple[str, str] | None:
    """Return (iso_date_str, format_name) or None."""
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
        # Include word-form amounts for max_amount calculation
        if not _parse_amount(block.text):
            word_result = _parse_word_amount(block.text)
            if word_result:
                amounts.append(word_result[1])
    return amounts


def _receipt_height(blocks: list[OcrBlock]) -> int:
    """Estimate total receipt height from bboxes."""
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
    """True if the value is an integer in the year range 1900–2099."""
    return val == int(val) and 1900 <= val <= 2099


# Word amount vocabulary for written-out amounts
_WORD_AMOUNT_VOCAB = {
    "ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE",
    "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN",
    "SEVENTEEN", "EIGHTEEN", "NINETEEN", "TWENTY", "THIRTY", "FORTY", "FIFTY",
    "SIXTY", "SEVENTY", "EIGHTY", "NINETY", "HUNDRED", "THOUSAND", "MILLION",
}


def _parse_word_amount(text: str) -> tuple[str, float] | None:
    """
    Parse written-out amounts like "SIXTY THREE ONLY" → ("63.00", 63.0).
    Returns None if text contains digits or no number words are found.
    Never raises — all errors return None.
    """
    try:
        # Uppercase and strip
        cleaned = text.upper().strip()

        # Skip if text contains any digit character
        if re.search(r"\d", cleaned):
            return None

        # Strip currency/noise tokens
        cleaned = re.sub(
            r"\bRINGGIT\s*MALAYSIA\b|\bRINGGIT\b|\bRM\b|\bMYR\b|\bONLY\b",
            "",
            cleaned,
            flags=re.IGNORECASE,
        )
        cleaned = cleaned.strip()

        # Guard: check if cleaned text contains at least one number word
        words = cleaned.split()
        if not any(word in _WORD_AMOUNT_VOCAB for word in words):
            return None

        # Split on "AND CENTS"
        parts = re.split(r"\s+AND\s+CENTS\s+", cleaned, maxsplit=1, flags=re.IGNORECASE)

        if len(parts) == 2:
            ringgit_part = parts[0].strip()
            cents_part = parts[1].strip()
            ringgit_value = w2n.word_to_num(ringgit_part)
            cents_value = w2n.word_to_num(cents_part)
            total = ringgit_value + (cents_value / 100.0)
        else:
            ringgit_part = cleaned.strip()
            ringgit_value = w2n.word_to_num(ringgit_part)
            total = float(ringgit_value)

        return (f"{total:.2f}", total)

    except Exception:
        return None


# ---------------------------------------------------------------------------
# Amount candidates
# ---------------------------------------------------------------------------


def extract_amount_candidates(blocks: list[OcrBlock]) -> list[Candidate]:
    if not blocks:
        return []

    all_amounts = _all_amounts_on_receipt(blocks)
    # Exclude year-like integers from the "largest amount" calculation so a
    # year fragment doesn't steal the +0.3 bonus from the real total.
    non_year_amounts = [v for v in all_amounts if not _is_year_like(v)]
    max_amount = max(non_year_amounts) if non_year_amounts else 0.0
    receipt_height = _receipt_height(blocks)

    candidates: list[Candidate] = []

    for idx, block in enumerate(blocks):
        parsed = _parse_amount(block.text)
        if not parsed:
            # Try word-amount fallback for written-out amounts
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

        block_upper = _block_text_upper(block)
        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0

        # Check surrounding context (same line and adjacent lines)
        context_blocks = [b for b in blocks if abs(b.line_index - block.line_index) <= 1]
        context_text = " ".join(_block_text_upper(b) for b in context_blocks)

        for norm_str, val, has_currency_prefix in parsed:
            score = 0.0

            # +0.4 if preceded by total keyword on same or previous line
            if _is_total_keyword(context_text) or _is_total_keyword(block_upper):
                score += 0.4

            # +0.3 if is largest non-year amount on receipt
            if val == max_amount and val > 0 and not _is_year_like(val):
                score += 0.3

            # +0.2 if in bottom 40% vertically
            if relative_y >= 0.60:
                score += 0.2

            # +0.3 if has explicit currency prefix ($, USD, RM, MYR)
            if has_currency_prefix:
                score += 0.3

            # -0.3 if adjacent to tax keyword
            if _is_tax_keyword(context_text):
                score -= 0.3

            # -0.2 if smaller than another amount on same line (likely unit price)
            same_line_amounts = []
            for b in blocks:
                if b.line_index == block.line_index:
                    for _, v, _ in _parse_amount(b.text):
                        same_line_amounts.append(v)
            if any(v > val for v in same_line_amounts):
                score -= 0.2

            # -0.5 if value looks like a calendar year (no decimal, 1900–2099)
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

            candidates.append(
                Candidate(
                    value=norm_str,
                    raw_text=block.text,
                    confidence=block.confidence,
                    heuristic_score=score,
                    block_index=idx,
                    features=features,
                )
            )

    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)


# ---------------------------------------------------------------------------
# Date candidates
# ---------------------------------------------------------------------------


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

        # Context: same line + adjacent lines (for date label detection)
        context_blocks = [b for b in blocks if abs(b.line_index - block.line_index) <= 1]
        context_text = " ".join(b.text for b in context_blocks)

        score = 0.0

        # +0.5 for machine-readable formats (unambiguous)
        if fmt_name in ("DD/MM/YYYY", "YYYY-MM-DD"):
            score += 0.5
        # +0.3 for human-readable month formats (US invoices, Malaysian receipts)
        elif fmt_name in ("DD Mon YYYY", "Mon DD YYYY"):
            score += 0.3

        # +0.3 if in top 30% of receipt/invoice
        if relative_y <= 0.30:
            score += 0.3

        # +0.2 if a date label keyword appears on the same or adjacent line
        if _DATE_LABEL_RE.search(context_text):
            score += 0.2

        # -0.2 if date is in the future
        try:
            parsed_dt = datetime.strptime(iso_date, "%Y-%m-%d")
            if parsed_dt > now:
                score -= 0.2
        except ValueError:
            pass

        features = {
            "ocr_confidence": block.confidence,
            "heuristic_score": score,
            "char_count": len(iso_date),
            "digit_ratio": sum(c.isdigit() for c in iso_date) / max(len(iso_date), 1),
            "is_all_caps": block.text.upper() == block.text,
            "line_index": block.line_index,
            "relative_y_position": relative_y,
            "relative_x_position": 0.0,
            "has_total_keyword_nearby": False,
            "has_tax_keyword_nearby": False,
            "is_largest_amount": False,
            "keyword_distance": 99,
        }

        candidates.append(
            Candidate(
                value=iso_date,
                raw_text=block.text,
                confidence=block.confidence,
                heuristic_score=score,
                block_index=idx,
                features=features,
            )
        )

    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)


# ---------------------------------------------------------------------------
# Merchant candidates
# ---------------------------------------------------------------------------

def extract_merchant_candidates(blocks: list[OcrBlock]) -> list[Candidate]:
    """
    Merchant name is typically in the first 3–5 lines, mixed or uppercase,
    high OCR confidence, and often contains a company-type suffix.
    """
    if not blocks:
        return []

    receipt_height = _receipt_height(blocks)
    candidates: list[Candidate] = []

    # Consider only blocks in the top 35% of the document
    top_blocks = (
        [b for b in blocks if _block_cy(b) / receipt_height <= 0.35]
        if receipt_height
        else blocks[:5]
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
        # Skip purely numeric or address-code-like blocks (e.g. "90375", "94104")
        digit_ratio = sum(c.isdigit() for c in text) / max(len(text), 1)
        if digit_ratio > 0.5:
            continue
        # Skip blocks that are amounts without letters
        if _parse_amount(text) and not re.search(r"[A-Za-z]{3,}", text):
            continue
        if _parse_date(text):
            continue

        block_cy = _block_cy(block)
        relative_y = block_cy / receipt_height if receipt_height else 0.0

        score = block.confidence

        # Bonus for uppercase (Malaysian receipts)
        if text.upper() == text and len(text) > 4:
            score += 0.1

        # Earlier lines get a bonus
        if block.line_index == 0:
            score += 0.2
        elif block.line_index == 1:
            score += 0.1

        # Strong bonus for recognisable company-name suffixes (Inc, LLC, PBC, SDN BHD…)
        if _COMPANY_SUFFIX_RE.search(text):
            score += 0.25

        # Penalty for short tokens
        if len(text) < 5:
            score -= 0.2

        # Penalty for high digit density (address codes, postal codes, PMB numbers)
        if digit_ratio > 0.3:
            score -= 0.2

        features = {
            "ocr_confidence": block.confidence,
            "heuristic_score": score,
            "char_count": len(text),
            "digit_ratio": digit_ratio,
            "is_all_caps": text.upper() == text,
            "line_index": block.line_index,
            "relative_y_position": relative_y,
            "relative_x_position": 0.0,
            "has_total_keyword_nearby": False,
            "has_tax_keyword_nearby": False,
            "is_largest_amount": False,
            "keyword_distance": 99,
        }

        candidates.append(
            Candidate(
                value=text,
                raw_text=text,
                confidence=block.confidence,
                heuristic_score=score,
                block_index=idx,
                features=features,
            )
        )

    return sorted(candidates, key=lambda c: c.heuristic_score, reverse=True)


# ---------------------------------------------------------------------------
# MyInvois e-invoice metadata extraction
# ---------------------------------------------------------------------------

# Standard UUID pattern (8-4-4-4-12 hex groups)
_UUID_PATTERN = re.compile(
    r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)

# LHDN TIN pattern: 1–2 uppercase letters followed by 10–11 digits
_TIN_PATTERN = re.compile(r"\b([A-Z]{1,2}[0-9]{10,11})\b")

# Invoice number labels (Malay and English)
_INVOICE_NO_LABEL_RE = re.compile(
    r"\b(Invoice\s*No\.?|No\.?\s*Invoice|No\.?\s*Invois|Inv\.?\s*No\.?|"
    r"Invoice\s*Number|Nombor\s*Invois)\b",
    re.IGNORECASE,
)

# MyInvois portal / LHDN detection signals
_MYINVOIS_SIGNAL_RE = re.compile(
    r"myinvois\.hasil\.gov\.my|MyInvois|LHDN\s+e.?Invois|e-Invoice\s+Malaysia",
    re.IGNORECASE,
)

# Labels that indicate a TIN value is nearby
_TIN_LABEL_RE = re.compile(
    r"\b(TIN|No\.?\s*Cukai|Tax\s*ID|Supplier\s*TIN|Penjual\s*TIN)\b",
    re.IGNORECASE,
)

# Invoice number value pattern — alphanumeric with hyphens/slashes, min 4 chars
_INVOICE_NUMBER_VALUE_RE = re.compile(r"[A-Z0-9][-A-Z0-9/]{3,63}", re.IGNORECASE)


def extract_einvoice_metadata(blocks: list[OcrBlock]) -> dict:
    """
    Detect MyInvois e-invoice signals in OCR blocks and extract:
      - is_einvoice (bool)
      - einvoice_uuid (str | None)
      - einvoice_number (str | None)
      - supplier_tin (str | None)

    Returns a dict with those four keys. All values default to False/None when
    the document does not appear to be a MyInvois validated e-invoice.
    """
    is_einvoice = False
    einvoice_uuid: str | None = None
    einvoice_number: str | None = None
    supplier_tin: str | None = None

    # --- 1. Detect MyInvois signals ---
    for block in blocks:
        if _MYINVOIS_SIGNAL_RE.search(block.text):
            is_einvoice = True
            break

    # --- 2. Extract UUID (LHDN validation UUID is present on validated e-invoices) ---
    for block in blocks:
        m = _UUID_PATTERN.search(block.text)
        if m:
            einvoice_uuid = m.group(0)
            is_einvoice = True  # A UUID on a receipt strongly implies MyInvois
            break

    # --- 3. Extract invoice number ---
    # Strategy: find a block containing an invoice-number label, then look at
    # the same block or the immediately following block for the actual reference.
    for idx, block in enumerate(blocks):
        if _INVOICE_NO_LABEL_RE.search(block.text):
            # Try the same block first (e.g. "Invoice No: INV-2025-001")
            same_block_without_label = _INVOICE_NO_LABEL_RE.sub("", block.text).strip(" :\t")
            m = _INVOICE_NUMBER_VALUE_RE.search(same_block_without_label)
            if m:
                einvoice_number = m.group(0).strip()
                break
            # Otherwise check the immediately following block
            if idx + 1 < len(blocks):
                next_text = blocks[idx + 1].text.strip()
                m2 = _INVOICE_NUMBER_VALUE_RE.match(next_text)
                if m2:
                    einvoice_number = m2.group(0).strip()
                    break

    # --- 4. Extract supplier TIN ---
    # Strategy: find a TIN-label block, then extract TIN from the same or next block.
    for idx, block in enumerate(blocks):
        if _TIN_LABEL_RE.search(block.text):
            m = _TIN_PATTERN.search(block.text)
            if m:
                supplier_tin = m.group(1)
                break
            if idx + 1 < len(blocks):
                m2 = _TIN_PATTERN.search(blocks[idx + 1].text)
                if m2:
                    supplier_tin = m2.group(1)
                    break

    return {
        "is_einvoice": is_einvoice,
        "einvoice_uuid": einvoice_uuid,
        "einvoice_number": einvoice_number,
        "supplier_tin": supplier_tin,
    }
