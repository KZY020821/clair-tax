# 17 — Definition of Done

A feature is done only if all conditions below are satisfied.

## Agent Completion Note

- “Done” means implemented, validated, and documented within the agreed task scope.
- If any item below is intentionally skipped, that skip should be called out explicitly as a known gap rather than implied to be complete.
- A feature can be technically merged while still failing this checklist, but it should not be described as complete unless these conditions are met or consciously waived by a human reviewer.

## Engineering
- code is committed on a feature branch
- tests added or explicitly justified
- CI passes
- lint/typecheck passes
- migrations reviewed

## Product
- acceptance criteria met
- UI states covered: loading, empty, error, success
- output is understandable by end user

## Security
- auth/authorization reviewed
- secrets not committed
- audit behavior considered for sensitive actions

## Ops
- logs included where needed
- rollback path known
- health checks still pass

## Documentation
- README or docs updated if behavior changed
- AGENTS.md updated if coding rules changed

## Review Use

- Use this checklist during task close-out, PR review, and pre-release verification.
- Earlier phase documents define what to build. This file defines the minimum bar for claiming that a scoped piece of work is finished.
