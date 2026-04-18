# AGENTS.md

## Spring Boot scope
This folder contains the primary Java backend for Clair Tax.

## Backend rules
- Keep business logic explicit and testable.
- Prefer clear service boundaries and small, focused classes.
- Keep controllers thin.
- Put domain logic in service or domain layers, not in controllers.
- Treat this service as the likely source of truth for tax and persistence rules unless the repository clearly says otherwise.

## Persistence
- Database changes should be migration-backed.
- Be careful when changing entity fields, nullability, or constraints.
- Keep Flyway migrations deterministic and reviewable.

## API design
- Use explicit request and response models.
- Validate external input.
- Return clear error responses.

## Quality
- Prefer readability over framework cleverness.
- Avoid hidden magic when a simple explicit implementation is clearer.
