# 13 — Phase 9: Testing and QA

## Objective
Build confidence that policy, calculation, and receipt workflows behave correctly.

## Agent Interpretation

- This phase is about risk reduction, not broad coverage for its own sake.
- Prioritize deterministic tests for the parts of the system that are financially sensitive, security-sensitive, or hard to debug after release.
- Where full end-to-end coverage is expensive, use focused integration tests and high-signal manual QA checklists to protect the critical path.

## Test Layers
### Backend
- unit tests for services
- integration tests for controllers + DB
- migration tests
- calculator table-driven tests

### Frontend
- component tests
- form validation tests
- route/render smoke tests
- end-to-end happy paths

### AI Service
- normalization tests
- worker processing tests
- provider abstraction tests

## Highest-risk Test Areas
1. progressive tax bracket math
2. relief cap enforcement
3. magic link expiration and replay
4. admin publish validation
5. receipt confirm/reject flow
6. duplicate receipt handling

## Codex Task 1
```md
Add backend calculator test suite.

Requirements:
- add table-driven tests for multiple policy years
- cover cap truncation and bracket edges
- verify zero and high-income scenarios
```

## Codex Task 2
```md
Add integration tests for auth and policy publish workflows.

Requirements:
- request link flow
- verify link flow
- publish draft policy success/failure cases
```

## Codex Task 3
```md
Add frontend end-to-end smoke tests.

Requirements:
- login request flow
- calculator estimate flow
- receipt review happy path with mocked backend
```

## Manual QA Checklist
- verify public yearly pages load fast
- verify year switching updates dynamic fields
- verify calculator output is explainable
- verify upload failures are recoverable
- verify admin cannot publish invalid drafts

## Dependencies and Handoff

- Depends on at least thin implementations of the main workflows so validations can run against real behavior.
- Should give deployment and production-readiness work confidence that the highest-risk user paths are covered.
- Human review should focus on whether the chosen tests match the known risk areas instead of maximizing raw test count.
