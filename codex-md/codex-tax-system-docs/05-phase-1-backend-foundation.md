# 05 — Phase 1: Backend Foundation

## Objective
Create a stable Spring Boot foundation with database connectivity, health checks, migrations, and base module structure.

## Agent Interpretation

- This phase establishes the backend delivery skeleton for later phases. It should produce a reliable foundation, not prematurely implement auth, policy, or calculator business rules.
- Treat package structure, migrations, config, and health behavior as the primary deliverables.
- If the repository already has generated framework code, use this file to normalize structure and conventions rather than replacing the existing scaffold blindly.

## Desired Outcome
Backend starts locally, connects to PostgreSQL, and exposes a simple health endpoint.

## Human Steps
1. Generate a Spring Boot app with Java 21.
2. Include dependencies:
   - Spring Web
   - Spring Security
   - Spring Data JPA
   - Validation
   - PostgreSQL Driver
   - Flyway
   - Actuator
   - Lombok if desired
3. Commit the generated scaffold before using Codex.

## First Codex Task
Use this prompt:

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

## Suggested Package Structure
```text
com.example.mytax
├── common
├── auth
├── policy
├── calculator
├── receipt
├── admin
└── config
```

## Deliverables
- application config
- health controller
- migration folder
- exception handler
- package structure

## Review Checklist
- no hardcoded secrets
- migration directory exists
- package naming is clean
- actuator/health not overexposed in prod config

## Dependencies and Handoff

- Depends on repository bootstrap, environment naming, and selected package conventions being decided.
- Should unblock authentication, policy, calculator, receipt, and admin work without forcing later modules to refactor the foundation.
- A successful output from this phase gives later agents obvious extension points for config, migrations, shared error handling, and health checks.
