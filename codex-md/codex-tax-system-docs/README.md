# Codex Build Workflow for Malaysian Personal Tax Management System

This document set simulates a full end-to-end build workflow using Codex for the Malaysian personal tax management system.

## Agent Reading Contract

- Read these files in order. Earlier files define intent and boundaries. Later files should be interpreted as implementation detail within those earlier constraints.
- Treat this folder as a planning and execution reference set, not proof that a live repository already matches every decision here.
- When repo reality differs from these docs, preserve the intent of the docs and document the deviation explicitly in the task output or PR description.
- Use `03-agents-and-rules.md` and `04-codex-working-model.md` as the default operating model before executing any phase-specific prompt.

## What is inside

- `00-project-charter.md` — project goals, scope, architecture summary
- `01-prerequisites.md` — accounts, tooling, local setup
- `02-repository-bootstrap.md` — repo structure and initialization
- `03-agents-and-rules.md` — AGENTS.md and operating rules for Codex
- `04-codex-working-model.md` — how to use Codex safely and effectively
- `05-phase-1-backend-foundation.md` — Spring Boot foundation
- `06-phase-2-authentication.md` — magic link authentication
- `07-phase-3-tax-policy-engine.md` — yearly tax rules and admin control
- `08-phase-4-tax-calculator.md` — calculator implementation
- `09-phase-5-receipt-ingestion.md` — upload, storage, OCR pipeline
- `10-phase-6-ai-extraction.md` — ML/OCR service workflow
- `11-phase-7-frontend-app.md` — Next.js app and UI workflow
- `12-phase-8-admin-portal.md` — admin pages and publishing workflow
- `13-phase-9-testing-and-qa.md` — testing, review, and acceptance
- `14-phase-10-deployment.md` — infrastructure and deployment
- `15-phase-11-security-and-operations.md` — hardening and ops
- `16-codex-prompt-library.md` — ready-to-paste Codex prompts
- `17-definition-of-done.md` — launch checklist
- `18-sample-AGENTS.md` — starter AGENTS.md
- `19-sample-issue-backlog.md` — example task breakdown
- `20-day-by-day-execution-plan.md` — suggested execution order

## Recommended way to use these docs

1. Read `00-project-charter.md`
2. Complete `01-prerequisites.md`
3. Apply `02-repository-bootstrap.md`
4. Add `18-sample-AGENTS.md` into the repo as `AGENTS.md`
5. Use prompts from `16-codex-prompt-library.md`
6. Execute each phase in order
7. Use `17-definition-of-done.md` before production

## Important principle

You are the architect. Codex is the implementation agent.

That means:
- you define architecture, database design, security boundaries, and acceptance criteria
- Codex generates code, tests, and revisions in small scoped tasks
- every change is reviewed before merge

## Agent Usage Notes

- Use one file in this set to define scope, then pair it with one prompt from `16-codex-prompt-library.md` or one issue from `19-sample-issue-backlog.md`.
- Do not treat a full phase document as a single implementation task. Each phase is a milestone that should be split into reviewable slices.
- Prefer shipping the minimum slice that unblocks the next phase instead of speculative completeness across multiple phases.
- When a task touches schema, auth, storage, or deployment, restate assumptions before finishing the task so reviewers can verify them quickly.

## Suggested repo structure

```text
tax-relief-app/
├── frontend/
├── backend/
├── ai-service/
├── infra/
├── docs/
├── .github/
├── docker-compose.yml
├── AGENTS.md
└── README.md
```
