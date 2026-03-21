# AGENTS.md

## How to Adapt This Template

- Replace generic names, package paths, and commands with the actual repository values before using this as a live `AGENTS.md`.
- Keep the intent of the rules even if the exact framework or command names differ in the real repo.
- Use this template together with `03-agents-and-rules.md` so repo-specific details do not weaken the project constraints.

## Project Overview
This repository contains a Malaysian personal tax management system.

## Services
- `frontend/` — Next.js application
- `backend/` — Spring Boot API
- `ai-service/` — FastAPI extraction service
- `infra/` — infrastructure as code

## Architecture Rules
- Backend starts as modular monolith.
- Tax rules must be data-driven.
- Receipt file content must not be stored in PostgreSQL.
- Controllers remain thin; services own business logic.

## Backend Standards
- Java 21
- Spring Boot
- JPA + Flyway
- Constructor injection
- DTOs for request/response
- BigDecimal for money

## Frontend Standards
- Next.js App Router
- TypeScript only
- TailwindCSS
- Zod for validation
- Central API client

## AI Service Standards
- FastAPI
- Pydantic models
- Provider abstractions for OCR/extraction
- Confidence score returned in extraction result

## Security Rules
- Never commit secrets.
- Never disable auth checks for convenience.
- Admin routes must be protected.
- Audit sensitive actions.

## Commands
### Backend
- run: `./mvnw spring-boot:run`
- test: `./mvnw test`

### Frontend
- run: `npm run dev`
- test: `npm test`
- lint: `npm run lint`

### AI Service
- run: `uvicorn app.main:app --reload --port 8001`
- test: `pytest`

## Review Expectations
Every change should include:
- concise explanation of files changed
- tests added or reason omitted
- assumptions made

## Template Boundary

- This file is a starter, not a substitute for repository discovery.
- If the real repository has stricter security, testing, or deployment rules, those stricter rules should be copied into the final `AGENTS.md`.
