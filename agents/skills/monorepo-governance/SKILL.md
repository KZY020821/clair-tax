---
name: monorepo-governance
description: Use this skill when a task spans multiple services in the Clair Tax monorepo, when deciding where code should live, or when refactoring shared responsibilities across frontend, Spring Boot, and FastAPI.
---

# Monorepo Governance Skill

## Purpose
Use this skill for cross-service work, architecture decisions, and changes that touch multiple parts of the repository.

## Core rules
- Keep responsibilities in the correct service.
- Do not place core tax domain logic in the frontend.
- Avoid duplicating rules in Spring Boot and FastAPI.
- Prefer explicit contracts between services.
- Keep changes small and reviewable even when the task spans multiple services.

## Decision guidance
- UI rendering, page composition, input flow, and interaction design belong in frontend.
- Persistent domain models, migrations, and authoritative business rules belong in springboot unless the repo clearly uses another source of truth.
- Python-centric processing, computation pipelines, and AI-supporting workflows belong in fastapi.
- Shared types should be copied deliberately or generated through an existing repo pattern, not improvised ad hoc.

## Cross-service changes
- When updating an API contract, update both producer and consumer.
- Preserve compatibility unless the task clearly permits breaking changes.
- Mention integration risks briefly when they matter.

## Output expectations
- Prefer implementation plans that respect service boundaries.
- Keep file placement intentional.
