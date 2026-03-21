# 04 — Codex Working Model

## How to think about Codex
Codex is best used as an implementation agent.

It should receive:
- a small task
- clear acceptance criteria
- constraints
- target files or modules
- commands to validate

## Agent Operating Assumptions

- A good Codex task names the target behavior, the affected area, the constraints, and the validation command set.
- When repo context is incomplete, Codex should make the smallest safe assumption and report it explicitly at the end of the task.
- If a request starts to span multiple bounded contexts, split it before implementation unless the user explicitly wants a broader refactor.
- Prefer finishing a small vertical slice with validation over touching many files with partial progress and unclear acceptance.

## Golden Rules
1. Never start with “build the whole app”.
2. Use one scoped task at a time.
3. Keep tasks small enough to review in one sitting.
4. Ask Codex to explain assumptions when a task touches architecture.
5. Review diffs before merge.
6. Merge only green builds.

## Good Task Format
Use this template:

```md
Task:
[one clear implementation task]

Context:
[where in repo, why this exists]

Requirements:
- ...
- ...

Constraints:
- ...
- ...

Validation:
- run ...
- tests should pass

Output:
- create/update files only inside ...
```

## Recommended Build Rhythm
### Human
- choose next issue
- define acceptance criteria
- assign to Codex
- review result
- merge or request changes

### Codex
- inspect repo
- edit code
- run tests
- produce diff / commit

## What Codex should not decide on its own
- legal interpretation of tax policy
- production security exceptions
- retention exemptions
- architecture rewrites
- schema-breaking migration strategy

## Best prompt size
Use 1 feature or 1 refactor at a time.

Good examples:
- scaffold Flyway migration for user tables
- implement request magic link endpoint
- add policy year CRUD API
- add calculator service for progressive brackets

Bad example:
- build the complete platform end-to-end

## Preferred Task Close-Out

When a task is finished, Codex should ideally report:
- what changed
- how it was validated
- assumptions or unresolved dependencies
- any follow-up task that was intentionally left for a later phase
