# 03 — Agents and Rules

## Purpose of AGENTS.md
AGENTS.md tells Codex how your repo works and what rules it must follow.

## Agent Interpretation

- Use this file as the source material for a repository-level `AGENTS.md` that translates project intent into enforceable working rules.
- The rules here apply across backend, frontend, AI, and infrastructure tasks unless a stricter repo-local rule overrides them.
- When a task-specific prompt omits safety or architecture details, fall back to this document before making assumptions.
- If real repository conventions differ, update the final `AGENTS.md` to be concrete, but keep the same intent around modularity, security, and reviewability.

## What AGENTS.md should contain
- repo overview
- service boundaries
- commands to run
- coding standards
- testing expectations
- security constraints
- folder ownership

## Mandatory Rules for This Project

### Architecture rules
- Keep backend as modular monolith unless specifically asked to extract a service.
- Do not hardcode Malaysian tax rules in source code when they belong in policy tables.
- Use DTOs for API I/O.
- Keep controllers thin and business logic in services.

### Security rules
- Never commit secrets.
- Never store receipt file blobs in PostgreSQL.
- Use pre-signed URLs for uploads/downloads where applicable.
- Do not bypass auth checks in admin routes.
- Sensitive actions must be audited.

### Database rules
- Use Flyway for migrations.
- Use UUID primary keys unless table requires numeric sequence.
- Add `created_at` and `updated_at` on business tables.
- Use soft-delete only where business logic demands it.

### Frontend rules
- Use App Router in Next.js.
- Use TypeScript only.
- Use server components when suitable.
- Use client components only when interaction requires it.
- Keep forms validated with Zod.

### AI service rules
- Separate extraction pipeline from user-facing backend.
- Return confidence values with extraction outputs.
- Make user correction override model output.

### Testing rules
- Every Codex task should include tests where possible.
- Calculator logic requires table-driven tests.
- Policy publishing requires integration tests.

## Required review checklist for every Codex PR
- Does this change fit the module boundary?
- Are tests included?
- Is any tax logic hardcoded incorrectly?
- Does this introduce security risk?
- Are migrations safe?
- Can this be rolled back?

## Escalation Triggers

Ask for explicit human review before finalizing changes that:
- rewrite module boundaries
- alter authentication or session strategy
- bypass audit requirements for sensitive actions
- change document retention or receipt storage assumptions
- introduce schema changes that are hard to roll back
