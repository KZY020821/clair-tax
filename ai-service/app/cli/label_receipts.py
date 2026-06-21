#!/usr/bin/env python3
"""
Interactive labeling assistant for clair-tax OCR training data.
Presents each receipt and helps you fill in ground truth values.
"""

import json
import sys
from pathlib import Path
from typing import Any, Dict, Optional

import structlog

logger = structlog.get_logger()


def load_annotations(path: Path) -> list:
    """Load JSONL annotations."""
    entries = []
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip():
                entries.append(json.loads(line))
    return entries


def extract_candidate_text(blocks: list, field_type: str) -> str:
    """Extract likely candidate text for a field from OCR blocks."""
    for block in blocks:
        if block.get(f'is_{field_type}', False):
            return block.get('text', '')
    return ""


def suggest_amount(blocks: list) -> Optional[str]:
    """Suggest amount from blocks flagged as amount or containing currency."""
    import re

    # First check blocks flagged as amount
    for block in blocks:
        if block.get('is_amount'):
            text = block.get('text', '')
            # Extract numeric value
            match = re.search(r'(\d+\.?\d*)', text.replace(',', ''))
            if match:
                return match.group(1)

    # Then look for common amount patterns
    for block in blocks:
        text = block.get('text', '')
        if re.search(r'(total|amount|gross|USD|RM|\$)\s*[:\-]?\s*(\d+\.?\d*)', text, re.IGNORECASE):
            match = re.search(r'(\d+\.?\d*)', text.replace(',', ''))
            if match:
                return match.group(1)

    return None


def suggest_date(blocks: list) -> Optional[str]:
    """Suggest date from blocks flagged as date or matching date patterns."""
    import re

    # First check blocks flagged as date
    for block in blocks:
        if block.get('is_date'):
            return block.get('text', '')

    # Then look for common date patterns
    date_patterns = [
        r'\b(\d{2}/\d{2}/\d{4})\b',
        r'\b(\d{4}-\d{2}-\d{2})\b',
        r'\b(\d{2}-\d{2}-\d{4})\b',
    ]

    for block in blocks:
        text = block.get('text', '')
        for pattern in date_patterns:
            match = re.search(pattern, text)
            if match:
                return match.group(1)

    return None


def suggest_merchant(blocks: list) -> Optional[str]:
    """Suggest merchant from blocks flagged as merchant or at top of receipt."""
    # First check blocks flagged as merchant
    for block in blocks:
        if block.get('is_merchant'):
            return block.get('text', '')

    # Then use first substantial text block (likely header)
    for block in sorted(blocks, key=lambda b: b.get('bbox', [[0,0]]*4)[0][1]):
        text = block.get('text', '').strip()
        if len(text) > 3 and len(text) < 100:
            # Skip common non-merchant words
            skip_words = ['receipt', 'invoice', 'bill', 'thank', 'dear']
            if not any(word in text.lower() for word in skip_words):
                return text

    return None


def label_interactive(entry: Dict[str, Any], index: int, total: int) -> Dict[str, Any]:
    """Interactive labeling for a single receipt."""
    print("\n" + "="*80)
    print(f"Receipt {index + 1}/{total}")
    print("="*80)
    print(f"Image: {entry.get('image_path', 'N/A')}")
    print(f"Receipt ID: {entry.get('receipt_id', 'N/A')}")

    blocks = entry.get('blocks', [])

    # Show current ground truth (if any)
    current_amount = entry.get('ground_truth_amount')
    current_date = entry.get('ground_truth_date')
    current_merchant = entry.get('ground_truth_merchant')

    print("\nCurrent ground truth:")
    print(f"  Amount:   {current_amount or 'NOT SET'}")
    print(f"  Date:     {current_date or 'NOT SET'}")
    print(f"  Merchant: {current_merchant or 'NOT SET'}")

    # Show suggestions
    suggested_amount = suggest_amount(blocks)
    suggested_date = suggest_date(blocks)
    suggested_merchant = suggest_merchant(blocks)

    print("\nSuggestions from OCR:")
    print(f"  Amount:   {suggested_amount or 'None'}")
    print(f"  Date:     {suggested_date or 'None'}")
    print(f"  Merchant: {suggested_merchant or 'None'}")

    # Show relevant OCR blocks
    print("\nRelevant OCR blocks:")
    for i, block in enumerate(blocks):
        if block.get('is_amount') or block.get('is_date') or block.get('is_merchant'):
            print(f"  [{i}] {block.get('text', '')} (confidence: {block.get('confidence', 0):.2f})")

    # Interactive input
    print("\n--- Enter ground truth values ---")
    print("(Press Enter to keep current value, or type new value)")
    print("(Type 'skip' to skip this receipt)\n")

    # Amount
    amount_prompt = f"Amount [{current_amount or suggested_amount or ''}]: "
    amount = input(amount_prompt).strip()
    if amount.lower() == 'skip':
        return entry
    if not amount and (current_amount or suggested_amount):
        amount = current_amount or suggested_amount

    # Date
    date_prompt = f"Date [{current_date or suggested_date or ''}]: "
    date = input(date_prompt).strip()
    if date.lower() == 'skip':
        return entry
    if not date and (current_date or suggested_date):
        date = current_date or suggested_date

    # Merchant
    merchant_prompt = f"Merchant [{current_merchant or suggested_merchant or ''}]: "
    merchant = input(merchant_prompt).strip()
    if merchant.lower() == 'skip':
        return entry
    if not merchant and (current_merchant or suggested_merchant):
        merchant = current_merchant or suggested_merchant

    # Update entry
    if amount:
        entry['ground_truth_amount'] = amount
    if date:
        entry['ground_truth_date'] = date
    if merchant:
        entry['ground_truth_merchant'] = merchant

    return entry


def main():
    annotations_path = Path("./data/annotations.jsonl")
    output_path = Path("./data/labeled.jsonl")

    if not annotations_path.exists():
        logger.error("annotations_not_found", path=str(annotations_path))
        sys.exit(1)

    print("="*80)
    print("OCR TRAINING DATA LABELING ASSISTANT")
    print("="*80)
    print(f"\nLoading annotations from: {annotations_path}")

    entries = load_annotations(annotations_path)
    print(f"Found {len(entries)} receipts to label\n")

    labeled_entries = []

    try:
        for i, entry in enumerate(entries):
            labeled_entry = label_interactive(entry, i, len(entries))
            labeled_entries.append(labeled_entry)

            # Auto-save after each entry
            with open(output_path, 'w', encoding='utf-8') as f:
                for entry in labeled_entries:
                    f.write(json.dumps(entry) + '\n')

            print(f"\n✓ Saved progress to {output_path}")

            # Ask if user wants to continue
            if i < len(entries) - 1:
                cont = input("\nContinue to next receipt? [Y/n]: ").strip().lower()
                if cont == 'n':
                    break

    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user. Progress saved.")

    # Final summary
    complete = sum(1 for e in labeled_entries
                   if e.get('ground_truth_amount') and
                      e.get('ground_truth_date') and
                      e.get('ground_truth_merchant'))

    print("\n" + "="*80)
    print("LABELING COMPLETE")
    print("="*80)
    print(f"Total receipts:     {len(labeled_entries)}")
    print(f"Fully labeled:      {complete}")
    print(f"Incomplete:         {len(labeled_entries) - complete}")
    print(f"Output file:        {output_path}")
    print("="*80)

    if complete < len(labeled_entries):
        print("\n⚠️  Warning: Some receipts are incomplete. Review before training.")
        print("   Incomplete entries will be skipped during training.")


if __name__ == '__main__':
    main()
