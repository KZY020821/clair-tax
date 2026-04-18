---
name: rest-api-design
description: Use this skill when designing or updating Clair Tax REST endpoints in Spring Boot, especially for request and response DTOs, validation, status codes, and API consistency.
---

# REST API Design Skill

## Purpose
Use this skill for HTTP contract design.

## Core rules
- Use explicit request and response models.
- Keep endpoint names and payloads consistent.
- Use appropriate status codes.
- Validate input and return actionable errors.

## Design guidance
- Prefer stable contracts over clever serialization tricks.
- Keep pagination, filtering, and sorting predictable when applicable.
- Avoid exposing internal entity structure directly unless the repo already does so intentionally.

## Output expectations
- Generate API code that is easy for the frontend to consume and easy to evolve safely.
