---
name: integration-architecture
description: Use this skill when wiring frontend, Spring Boot, FastAPI, and PostgreSQL together, especially for API contracts, request flows, serialization, database assumptions, and environment configuration.
---

# Integration Architecture Skill

## Purpose
Use this skill for end-to-end implementation across services.

## Core rules
- Keep API contracts explicit.
- Keep data flow predictable.
- Avoid silent assumptions about field names, nullability, or date formats.
- Preserve the backend as source of truth for business rules.

## Contract design
- Prefer typed request and response models.
- Keep naming consistent across layers unless there is a strong reason to transform.
- Handle failure states explicitly.

## Environment and config
- Never hardcode secrets.
- Prefer environment-based configuration.
- Keep service URLs, ports, and credentials configurable.

## Database awareness
- Treat database schema changes as migration-backed.
- Do not change persisted models without checking migration impact.
- Keep column meaning and domain meaning aligned.

## Output expectations
- Include integration-safe error handling.
- Favor production-oriented code over placeholders.
