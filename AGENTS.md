# Clair Tax Repo Guide

## Overview

This repository contains the Clair Tax platform bootstrap:

- `frontend/` for the Next.js App Router web application
- `backend/` for the Spring Boot modular monolith API
- `ai-service/` for the FastAPI AI/OCR orchestration service
- `mobile-app/` for the future Expo mobile client scaffold
- `infra/` for infrastructure assets
- `docs/` for repository-owned documentation

## Service Boundaries

- Keep the backend as a modular monolith unless a task explicitly asks for service extraction.
- Keep Malaysian tax policy and calculation truth in backend-managed data and services, not in frontend or mobile code.
- Keep the AI service focused on extraction and orchestration. User corrections must remain able to override model output.
- Treat `mobile-app/` as a thin client boundary that mirrors web semantics without owning tax rules.

## Commands

- Frontend: `cd frontend && pnpm dev`, `pnpm lint`, `pnpm test`, `pnpm build`
- Backend: `cd backend && ./mvnw spring-boot:run`, `./mvnw test`
- AI service: `ai-service/.venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`, `ai-service/.venv/bin/pytest ai-service/tests`
- Compose: `docker compose up --build`

## Coding Standards

- Use TypeScript in `frontend/`.
- Use the Next.js App Router and prefer server components unless interaction requires a client component.
- Keep backend controllers thin and push business logic into services.
- Use DTOs or response records for API I/O.
- Use Flyway for backend schema evolution.
- Prefer UUID primary keys and audit-friendly schemas in backend persistence.
- Keep local configuration env-driven and never commit real secrets.

## Security Constraints

- Never commit secrets or provider-issued credentials.
- Never store receipt binaries in PostgreSQL.
- Preserve authentication checks and auditability for sensitive actions.
- Prefer pre-signed upload/download flows when receipt storage is implemented.

## Testing Expectations

- Every meaningful change should add or update tests where practical.
- Frontend changes should keep `pnpm lint`, `pnpm test`, and `pnpm build` green.
- Backend changes should keep `./mvnw test` green.
- AI service changes should keep `pytest` and `ruff check` green.

# AGENTS.md

## Project
Malaysian personal tax management system with web and mobile clients.

## Architecture
- frontend: Next.js + TypeScript
- backend: Spring Boot + Java 21
- ai-service: FastAPI + Python 3.11
- database: PostgreSQL
- storage: AWS S3
- queue: AWS SQS

## Rules
- Do not hardcode tax rules in application logic.
- Tax policy must be data-driven and versioned by year.
- Keep implementations small and production-oriented.
- Prefer clear folder structure and explicit types.
- Add or update tests for every feature.
- Do not introduce new frameworks unless requested.
- Keep secrets out of the repo.

## Backend conventions
- controller -> service -> repository layering
- DTOs for API requests/responses
- Flyway for database migrations
- Validation on all external inputs

## Frontend conventions
- App Router
- TypeScript only
- TailwindCSS
- TanStack Query for server state
- Zod for client validation
- Always prioritize UI simplicity and cleanliness first. Screens should feel calm, easy to scan, and intentionally minimal before any decorative treatment is considered.
- Use a restrained Clair Tax brand system anchored on `#FFFFFF`, `#000000`, and `#5E9BFF`, with any tints or borders derived from that palette instead of introducing unrelated accent colors.
- Keep the default application shell old-school and operational: sticky header, primary left sidebar, main content column, and footer.
- Prefer rounded surfaces across panels, inputs, buttons, navigation rails, and tables; define the canonical radii in `frontend/tailwind.config.ts` and shared classes in `frontend/app/globals.css`.
- Keep global branding tokens in `frontend/tailwind.config.ts` and `frontend/app/globals.css` so page and component styling inherits from the root instead of re-defining colors ad hoc.
- Keep page composition traditional and practical for tax work: simple header content, a clear sidebar hierarchy, broad whitespace, and low-noise cards over flashy layouts.
- For tax workflows, favor calm, professional layouts over flashy marketing patterns or overly animated UI.

## AI service conventions
- FastAPI
- Pydantic models
- Clear separation between OCR adapter and business logic

## Definition of done
- code compiles
- tests pass
- README updated if setup changes
