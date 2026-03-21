# 16 — Codex Prompt Library

These are ready-to-paste prompts.

## How to Use These Prompts

- Use one prompt at a time and pair it with current repository context, target paths, and validation commands from the actual workspace.
- Treat each prompt as a starting point. Add local package names, branch conventions, and existing module names without changing the intent of the task.
- If a prompt touches migrations, security, storage, or deployment, append explicit acceptance criteria so Codex can close the task with fewer assumptions.
- Keep prompts scoped. If one prompt starts to expand into multiple bounded contexts, split it before execution.

## Recommended Prompt Suffix

Append a short repo-specific suffix like this when using a prompt:

```md
Repo notes:
- affected module(s): ...
- target file paths or packages: ...
- validation commands: ...
- do not modify: ...
```

## Prompt 1 — Backend foundation
```md
Task:
Scaffold the backend foundation for a Spring Boot service.

Context:
This is the backend for a Malaysian personal tax management platform.

Requirements:
- Create package structure for auth, policy, calculator, receipt, admin, common
- Add `/api/health` endpoint returning service status
- Configure PostgreSQL using environment variables
- Configure Flyway and add initial migration placeholder
- Add standard API error response model

Constraints:
- Do not implement business logic yet
- Keep code modular
- Use constructor injection

Validation:
- backend should compile
- health endpoint should return 200
- Flyway migration should run on startup
```

## Prompt 2 — Magic link request
```md
Task:
Implement magic link request flow.

Requirements:
- POST `/api/auth/request-link`
- Accept email
- Create user if not exists
- Create one-time token record with hashed token
- Stub email sender service interface
- Return generic success message

Constraints:
- Do not reveal whether email exists
- Add validation and rate-limiting hook points
- Add Flyway migration
- Add unit tests for token creation and expiry handling
```

## Prompt 3 — Magic link verify
```md
Task:
Implement magic link verification flow.

Requirements:
- POST `/api/auth/verify`
- Accept token and email
- Validate active token
- Mark token as used
- Issue access token and refresh token
- Return session response DTO

Constraints:
- Reject expired or replayed token
- Audit successful login
- Add integration tests for happy path and expired token path
```

## Prompt 4 — Policy engine
```md
Task:
Implement policy year and tax bracket domain.

Requirements:
- Create JPA entities and Flyway migrations for policy_year and tax_bracket
- Add admin CRUD APIs for draft policy years and brackets
- Add service layer and DTOs
- Add validation to prevent overlapping bracket ranges per year

Constraints:
- Published policy years cannot be edited directly
- Keep controllers thin
- Add service unit tests
```

## Prompt 5 — Relief categories
```md
Task:
Implement relief category and relief rule domain.

Requirements:
- Create entities, migrations, repositories, services, controllers
- Support rule config as JSON field
- Add admin API to clone all relief categories from one policy year to another
- Add integration tests for clone flow
```

## Prompt 6 — Calculator
```md
Task:
Implement calculator service.

Requirements:
- Create user_tax_profile and user_relief_claim tables
- Load published policy by year
- Compute capped relief per category
- Compute chargeable income and progressive tax
- Return full breakdown DTO

Constraints:
- Use BigDecimal for money
- Add table-driven tests
```

## Prompt 7 — Receipt metadata
```md
Task:
Implement receipt metadata and upload intent APIs.

Requirements:
- Create receipts and extraction result tables
- Add upload intent endpoint for authenticated users
- Persist selected year and relief category
- Return upload contract DTO

Constraints:
- do not store file blobs in PostgreSQL
- include hash field for duplicate detection
```

## Prompt 8 — Receipt processing
```md
Task:
Implement receipt processing integration.

Requirements:
- Add S3 storage abstraction
- Add queue dispatch abstraction for receipt jobs
- Persist receipt after upload confirmation
- Add review/confirm API

Constraints:
- idempotent queue dispatch
- user correction overrides model output
```

## Prompt 9 — FastAPI extraction service
```md
Task:
Scaffold FastAPI extraction service.

Requirements:
- health endpoint
- extraction result schema
- provider abstraction
- worker skeleton
- tests
```

## Prompt 10 — Frontend scaffold
```md
Task:
Scaffold Next.js frontend application.

Requirements:
- Use App Router and TypeScript
- Add TailwindCSS
- Create route groups for public, app, and admin
- Add placeholder pages for login, dashboard, calculator, receipts, admin
```

## Prompt 11 — Calculator UI
```md
Task:
Implement calculator UI.

Requirements:
- Year selector
- Gross income input
- Dynamic relief fields from backend
- Result panel with tax breakdown
- Save action for authenticated users
```

## Prompt 12 — Admin UI
```md
Task:
Implement admin policy pages and publish flow.

Requirements:
- list policy years
- create draft
- clone previous year
- edit brackets and reliefs
- publish with confirmation modal
- show validation summary
```
