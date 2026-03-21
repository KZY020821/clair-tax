# 15 — Mobile Definition of Done

A feature is done only when all conditions below are true.

## Agent Completion Note

- “Done” means the mobile feature works in the intended user journey, handles expected states, and stays aligned with backend and web semantics.
- If any checklist item is intentionally deferred, call it out explicitly as a known gap instead of implying completeness.
- This checklist is the bar for claiming completion of a scoped task, not a substitute for the scope definition itself.

## Engineering
- code compiles
- lint passes
- tests pass
- no TypeScript any leakage without justification
- no dead routes or unused screens

## Product
- labels match web terminology
- flow matches the intended journey
- empty state and error state are implemented
- loading state is implemented

## Backend integration
- DTO types are documented
- API errors are handled
- auth edge cases are handled

## UX parity
- same major modules as web
- same tax result fields as web
- same receipt states as web
- same year selection behavior as web

## Observability
- errors logged
- key actions tracked
- release notes updated if user-visible

## Review
- diff is scoped
- screenshot or recording attached for UI changes
- documentation updated where behavior changed

## Review Use

- Use this file during task close-out and release review to ensure parity, resilience, and observability have not been skipped.
- Earlier mobile docs explain what to build. This file explains when that scoped work should be considered complete.
