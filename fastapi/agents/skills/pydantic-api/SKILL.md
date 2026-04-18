---
name: pydantic-api
description: Use this skill when defining or updating Clair Tax request and response models in FastAPI, including validation, serialization, nested models, and field-level constraints.
---

# Pydantic API Skill

## Purpose
Use this skill for request and response schema design.

## Core rules
- Define clear Pydantic models.
- Validate fields intentionally.
- Keep field names consistent with API contracts.
- Handle optional and nullable fields carefully.

## Output expectations
- Use explicit models rather than loose untyped dictionaries where practical.
- Keep serialization behavior easy to understand.
