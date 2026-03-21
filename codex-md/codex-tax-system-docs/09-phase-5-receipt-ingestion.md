# 09 — Phase 5: Receipt Ingestion

## Objective
Build secure receipt upload, metadata persistence, asynchronous processing, and category-cap consumption.

## Agent Interpretation

- This phase is about receipt lifecycle orchestration. It should connect upload, metadata, processing, and review states without embedding file blobs or OCR assumptions directly into the core database.
- Treat metadata accuracy, auditability, and user review flow as first-class requirements alongside the upload mechanics.
- Prefer incremental delivery: upload intent, storage confirmation, queue dispatch, and review endpoints can be implemented in narrow slices as long as lifecycle semantics stay clear.

## Functional Flow
1. User chooses year and category.
2. User uploads receipt.
3. Receipt file goes to S3.
4. Backend stores metadata.
5. Message is queued.
6. OCR/AI service extracts amount and date.
7. User reviews extracted values.
8. Confirmed amount contributes to category usage.

## Recommended Tables
- receipts
- receipt_extraction_result
- receipt_review_action

## Suggested Receipt Columns
### receipts
- id
- user_id
- policy_year_id
- relief_category_id
- s3_key
- original_filename
- mime_type
- file_size_bytes
- sha256_hash
- status (`uploaded`, `processing`, `processed`, `verified`, `rejected`)
- uploaded_at
- created_at
- updated_at

### receipt_extraction_result
- id
- receipt_id
- extracted_amount
- extracted_date
- merchant_name
- confidence_score
- raw_payload_json
- model_version
- created_at

## Codex Task 1
```md
Implement receipt metadata domain.

Requirements:
- Create Flyway migrations and JPA entities for receipts and extraction results
- Add API to create upload intent for authenticated users
- Store selected tax year and relief category
- Return placeholder upload contract DTO

Constraints:
- do not store file blobs in PostgreSQL
- include sha256 hash field for duplicate detection
```

## Codex Task 2
```md
Implement S3 upload integration and queue dispatch.

Requirements:
- Backend creates pre-signed upload URL or server-side upload flow abstraction
- Persist receipt record after upload confirmation
- Send receipt processing message to SQS
- Add audit log entry

Constraints:
- make queue dispatch idempotent
- isolate AWS integration behind interfaces
```

## Codex Task 3
```md
Implement receipt review API.

Requirements:
- GET receipt details with latest extraction result
- POST review/confirm extracted amount and date
- POST reject extraction
- On confirm, update category usage summary for user tax profile

Constraints:
- user correction always overrides model output
- maintain review history
```

## Review Checklist
- file is stored in S3 only
- metadata is enough to audit file lifecycle
- idempotency considered
- user review path exists before tax usage is applied

## Dependencies and Handoff

- Depends on authenticated users, published relief categories, object storage strategy, and queue conventions.
- Should unblock the AI extraction service, frontend receipt review pages, and mobile capture flows while preserving user correction as the source of truth.
- Human review should focus on status transitions, duplicate detection strategy, idempotent queue dispatch, and whether receipt confirmation happens only after review.
