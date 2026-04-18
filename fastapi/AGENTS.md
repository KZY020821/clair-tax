# AGENTS.md

## FastAPI scope
This folder contains the Python FastAPI service for Clair Tax.

## Service rules
- Keep endpoints explicit and well typed.
- Prefer Pydantic models for request and response contracts.
- Keep business logic out of route handlers when it grows beyond simple orchestration.
- Write Python that is readable, explicit, and easy to test.

## API quality
- Validate input at the edge.
- Handle exceptions with clear HTTP responses.
- Keep async usage intentional; do not introduce async everywhere without reason.

## Boundaries
- Do not duplicate complex core tax rules here if Spring Boot is the source of truth.
- Use this service for Python-native workflows, auxiliary capabilities, or delegated processing.
