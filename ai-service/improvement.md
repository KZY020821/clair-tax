Yes, the service has two layers and both can be improved:

---

## Layer 1: The heuristic scorer (always active)

The rules in [`normalization.py`](ai-service/app/services/normalization.py) are the foundation. These aren't "trained" in the ML sense — they're hand-written. You improve them by:

- **Adding keywords** to `TOTAL_KEYWORDS` / `TAX_KEYWORDS` when you spot new patterns in failed extractions
- **Adjusting score weights** (the `+0.4`, `+0.3` etc.) based on what receipts/invoices you're processing
- **Extending regex patterns** for new date or amount formats you encounter

This is the fastest iteration loop — edit, re-test with `curl`, no training required.

---

## Layer 2: The sklearn model (opt-in, currently off)

The full training pipeline already exists in the codebase. It's a `RandomForestClassifier` wrapped in `CalibratedClassifierCV` (for calibrated probabilities). To use it:

### Step 1 — Collect samples
Drop receipt/invoice image files into `ai-service/samples/`.

### Step 2 — Generate annotation manifest
```bash
cd ai-service
python -m app.cli.prepare_receipt_annotations \
  --input-dir ./samples \
  --output ./data/annotations.jsonl
```
This runs OCR on every sample and writes a JSONL file — one entry per receipt with all the OCR blocks.

### Step 3 — Label the data
Open `data/annotations.jsonl` and for each receipt, fill in the ground truth:
```json
{
  "receipt_id": "sample-001",
  "ground_truth_amount": "200.00",
  "ground_truth_date": "2026-04-17",
  "ground_truth_merchant": "Anthropic, PBC",
  "blocks": [...]
}
```

### Step 4 — Train
```bash
python -m app.cli.train_receipt_models \
  --manifest ./data/labeled.jsonl \
  --output ./model_artifacts/receipt_postprocessor.joblib \
  --candidate-table-dir ./data/tables
```

### Step 5 — Evaluate
```bash
python -m app.cli.evaluate_receipt_models \
  --manifest ./data/labeled.jsonl \
  --artifact ./model_artifacts/receipt_postprocessor.joblib
```
Prints precision/recall/F1 per field.

### Step 6 — Enable it
```dotenv
# ai-service/.env
TRAINED_RECEIPT_POSTPROCESSOR_ENABLED=true
```

The model re-scores every candidate using the 12 features in the feature vector (OCR confidence, position, keyword proximity, etc.) rather than the fixed heuristic weights.

---

## Other meaningful improvement paths

| Approach | Effort | Impact |
|---|---|---|
| **Label more samples** (50–100 diverse receipts) | Medium | High — model generalises better across receipt types |
| **Add document-type detection** | Medium | High — invoices and receipts have different layouts; apply different keyword sets per type |
| **Pre-process PDFs at higher DPI** (200–300 vs current 150) | Low | Medium — better OCR accuracy on small text |
| **Switch OCR engine to PaddleOCR** | Medium | Medium — better accuracy on printed documents than EasyOCR, which was designed for scene text |
| **Add a layout-aware pass** | High | High — use block x-position to identify label/value column pairs (e.g. "Amount Due" left-aligned, "$200.00" right-aligned) |
| **Use an LLM as a post-processing step** | Low (API call) | Very High — pass OCR text to Claude and ask it to extract structured fields; much more robust than regex |

The **LLM post-processing** option is worth highlighting: instead of regex heuristics, you'd pass the raw OCR text to Claude via the Anthropic API and ask for JSON output. It handles any language, any format, any layout, with no training data needed. The tradeoff is latency (~1–2s) and per-receipt API cost.