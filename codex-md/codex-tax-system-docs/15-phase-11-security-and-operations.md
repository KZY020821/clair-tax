# 15 — Phase 11: Security and Operations

## Objective
Harden the platform for production and ongoing operations.

## Agent Interpretation

- This phase adds cross-cutting controls that make the system supportable and trustworthy in production.
- Prefer reusable abstractions for audit, correlation, logging, and health over one-off instrumentation inside feature code.
- Security and operations work should make risky flows easier to trace and recover, not just harder to abuse.

## Security Controls
- rate limiting on auth endpoints
- hashed one-time tokens
- secure session handling
- admin role enforcement
- audit logs on sensitive actions
- S3 encryption and restricted IAM
- signed upload and download flows
- retention and lifecycle policy on receipts

## Operational Controls
- central logs
- health checks
- alerts for queue backlog
- alerts for failed OCR jobs
- DB backup policy
- runbook for rollback

## Codex Task 1
```md
Add security middleware and audit hooks.

Requirements:
- add request correlation id
- add audit service abstraction
- log admin publish, receipt review, and successful login events
- add rate-limit filter placeholder for auth endpoints
```

## Codex Task 2
```md
Add operational readiness docs and config.

Requirements:
- health and readiness endpoints
- structured logging config
- example alerting checklist in docs
```

## Production Readiness Review
- can you trace who changed tax policy?
- can you restore a receipt if user reports a problem?
- can you explain every calculator output?
- can you replay failed receipt jobs safely?

## Dependencies and Handoff

- Depends on the core platform flows already existing so controls can wrap real behavior.
- Should unblock launch review, incident response, and future compliance conversations by making system behavior observable and auditable.
- Human review should focus on whether the added controls meaningfully improve traceability, recovery, and operator confidence.
