# AI Service

FastAPI-based local app plus an AWS Lambda SQS worker for receipt extraction.

## What it does

- exposes `/health` and `/api/demo-summary` for local development
- processes receipt jobs delivered through SQS in production
- downloads uploaded receipt files from S3
- runs an OCR/provider extraction step
- normalizes total amount, receipt date, merchant name, currency, confidence, and warnings
- optionally runs a trained post-processor that re-scores amount/date candidates and rejects invalid receipts with explicit error codes
- writes processing attempts and extraction results back to the Spring Boot backend

## Local setup

1. Create a virtual environment:
   `python3 -m venv .venv`
2. Activate it:
   `source .venv/bin/activate`
3. Install dependencies:
   `pip install -r requirements.txt`
4. Start the app:
   `uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`

## Tests

- `pytest ai-service/tests`
- `ruff check ai-service/app ai-service/tests`

## Training workflow

1. Scaffold a review manifest from a folder of sample files:
   `python -m app.cli.prepare_receipt_annotations --input-dir ./samples --output ./data/annotations.jsonl`
2. Fill in labels and attach OCR payload JSON paths or inline payloads in the manifest.
3. Train the receipt post-processor and export candidate tables:
   `python -m app.cli.train_receipt_models --manifest ./data/labeled.jsonl --output ./model_artifacts/receipt_postprocessor.joblib --candidate-table-dir ./data/tables`
4. Evaluate an existing artifact on a labeled manifest:
   `python -m app.cli.evaluate_receipt_models --manifest ./data/labeled.jsonl --artifact ./model_artifacts/receipt_postprocessor.joblib`

## Lambda entrypoint

- handler: `app.handlers.sqs.lambda_handler`

## Key environment variables

- `BACKEND_API_BASE_URL`
- `BACKEND_INTERNAL_TOKEN`
- `AWS_REGION`
- `DEFAULT_RECEIPT_CURRENCY`
- `TRAINED_RECEIPT_POSTPROCESSOR_ENABLED`
- `TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH`
- `RECEIPT_AMOUNT_SELECTION_THRESHOLD`
- `RECEIPT_DATE_SELECTION_THRESHOLD`
- `RECEIPT_VALIDITY_THRESHOLD`
