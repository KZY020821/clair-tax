# 10 — Phase 6: AI Extraction Service

## Objective
Build a Python service to process receipt extraction jobs and return structured fields.

## Agent Interpretation

- This service is an extraction worker boundary, not the owner of business truth for receipt approval or tax policy.
- The important outcome is a replaceable pipeline that can normalize provider output into a stable structure consumed by the main system.
- Keep extraction outputs explicit about confidence and raw payload so downstream review flows can remain explainable and auditable.

## Scope
Start simple:
- accept receipt processing job
- retrieve file from S3
- call OCR / extraction provider
- normalize output
- save extraction result back through backend API or DB access layer

## Service Layout
```text
ai-service/
├── app/
│   ├── api/
│   ├── services/
│   ├── models/
│   ├── workers/
│   └── clients/
├── tests/
└── requirements.txt
```

## MVP Extraction Fields
- total_amount
- receipt_date
- merchant_name
- currency
- confidence_score

## Codex Task 1
```md
Scaffold FastAPI extraction service.

Requirements:
- Create FastAPI app structure
- Add health endpoint
- Add worker-friendly processing service interface
- Add pydantic models for extraction result
- Add unit tests
```

## Codex Task 2
```md
Implement receipt extraction pipeline abstraction.

Requirements:
- Create service interface for OCR provider
- Add placeholder provider implementation returning normalized output structure
- Add normalization logic for amount/date parsing
- Return confidence score and raw payload

Constraints:
- provider must be swappable
- no hardcoded backend URLs
```

## Codex Task 3
```md
Implement queue-consumer worker skeleton.

Requirements:
- Add job processor that accepts receipt id and s3 key
- Fetch file through storage abstraction
- Call extraction provider
- Persist structured result through backend client abstraction
- Add retry-safe logging
```

## Later Enhancement
After MVP, train or fine-tune a better receipt parser using corrected user data.

## Review Checklist
- extraction is replaceable
- normalization is tested
- user corrections remain source of truth

## Dependencies and Handoff

- Depends on receipt metadata, storage access, queue contracts, and a backend or persistence integration path.
- Should unblock receipt review workflows by returning structured candidate data, not by making final user-facing decisions.
- Human review should focus on swappable provider boundaries, retry safety, and whether corrected user values can always override extracted values.
