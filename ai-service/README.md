# Clair Tax AI Service

FastAPI receipt OCR microservice for the Clair Tax Malaysian personal tax management system.

- **Local (`APP_ENV=local`)**: HTTP server on port 8000, called directly by Spring Boot
- **Production (`APP_ENV=production`)**: AWS Lambda container triggered by SQS

## Architecture

```
Receipt upload (frontend)
    → Spring Boot stores file in S3, queues SQS job
        → Lambda downloads from S3, runs EasyOCR
            → Normalise (amount, date, merchant)
                → Postprocess (heuristic or trained model)
                    → Write extraction result back to Spring Boot
```

## Prerequisites

- Python 3.11+
- AWS account with S3 bucket (S3 is the only AWS service used)
- Spring Boot backend running on port 8080

## One-Time Setup

```bash
cd ai-service

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt
pip install -r requirements-dev.txt  # for tests and linting

# Copy and fill in environment variables
cp .env.example .env
# Edit .env — fill in AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, S3_BUCKET_NAME
```

### AWS IAM setup (minimal permissions)

Create an IAM user with this inline policy (replace `YOUR_BUCKET_NAME`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:HeadObject"],
      "Resource": "arn:aws:s3:::YOUR_BUCKET_NAME/*"
    }
  ]
}
```

Never use root credentials. Create an access key for the IAM user and paste into `.env`.

## Full Stack Local Development

### Terminal 1: PostgreSQL

Start your local PostgreSQL instance (e.g. via Homebrew or Docker):

```bash
# Homebrew
brew services start postgresql@15

# Docker
docker run -d \
  --name clair-postgres \
  -e POSTGRES_DB=clair_tax \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15
```

### Terminal 2: Spring Boot Backend

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

Wait for: `Started ClairTaxApplication in X seconds`

### Terminal 3: Next.js Frontend

```bash
cd frontend
bun install
bun run dev
```

Wait for: `ready started server on 0.0.0.0:3000`

### Terminal 4: AI Service

```bash
cd ai-service
source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Wait for: `Application startup complete.`

## Verify Everything Is Running

```bash
# Spring Boot health
curl http://localhost:8080/api/health

# AI service health
curl http://localhost:8000/health

# AI service OCR health (runs EasyOCR on synthetic image)
curl http://localhost:8000/api/health/ocr

# Frontend
open http://localhost:3000
```

## Running Tests

```bash
cd ai-service
source .venv/bin/activate
pytest tests/                           # all tests
pytest tests/unit/                      # unit tests only
pytest tests/integration/               # integration tests only
pytest -v tests/unit/test_normalization.py  # single file
```

## Linting

```bash
ruff check app/ tests/
ruff check --fix app/ tests/  # auto-fix where possible
```

## Demo Endpoint (No S3 Required)

Upload a receipt image directly to get OCR extraction without S3:

```bash
curl -X POST http://localhost:8000/api/demo-summary \
  -F "file=@/path/to/receipt.jpg"
```

Response:
```json
{
  "receipt_id": "demo-receipt.jpg",
  "extraction_status": "extracted",
  "amount": "32.54",
  "currency": "MYR",
  "date": "2024-03-15",
  "merchant_name": "MYDIN SUPERMARKET SDN BHD",
  "amount_confidence": 0.97,
  "date_confidence": 0.92,
  "merchant_confidence": 0.95,
  "raw_ocr_block_count": 22,
  "processing_mode": "heuristic"
}
```

## Training a Custom Model

```bash
# 1. Generate annotation manifest from sample images
python -m app.cli.prepare_receipt_annotations \
  --input-dir ./samples \
  --output ./data/annotations.jsonl

# 2. Open ./data/annotations.jsonl and manually label each block:
#    Set is_amount=true, is_date=true, is_merchant=true on correct blocks
#    Set ground_truth_amount, ground_truth_date, ground_truth_merchant

# 3. Train model
python -m app.cli.train_receipt_models \
  --manifest ./data/labeled.jsonl \
  --output ./model_artifacts/receipt_postprocessor.joblib \
  --candidate-table-dir ./data/tables

# 4. Evaluate model
python -m app.cli.evaluate_receipt_models \
  --manifest ./data/labeled.jsonl \
  --artifact ./model_artifacts/receipt_postprocessor.joblib

# 5. Enable in .env
echo "TRAINED_RECEIPT_POSTPROCESSOR_ENABLED=true" >> .env
```

## Production Deployment (Lambda)

```bash
# Build Docker image
docker build -t clair-tax-ai-service .

# Tag and push to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

docker tag clair-tax-ai-service:latest \
  123456789.dkr.ecr.us-east-1.amazonaws.com/clair-tax-ai-service:latest

docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/clair-tax-ai-service:latest
```

Set Lambda environment variables to match `.env.example` (production values).

## Extraction Status Reference

| Status | Meaning |
|---|---|
| `extracted` | Amount + at least one of date/merchant found |
| `partial` | Amount found but no date or merchant |
| `invalid` | No amount found |
| `no_text_detected` | OCR returned zero blocks |
| `failed` | Processing error (S3, OCR, or unexpected) |
