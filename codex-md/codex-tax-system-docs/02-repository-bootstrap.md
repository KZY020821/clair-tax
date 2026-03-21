# 02 — Repository Bootstrap

## Goal
Set up the repo so Codex has a clean, predictable workspace.

## Agent Interpretation

- This document defines the minimum repository scaffolding that makes later Codex tasks safer and more predictable.
- Treat the listed folders and root files as baseline workspace affordances, not as a request to prebuild business features.
- Prefer light scaffolding, placeholders, and documented commands over speculative implementation inside every service folder.
- If the repo already exists, use this file as a gap checklist rather than recreating structure blindly.

## Directory Layout
```text
tax-relief-app/
├── frontend/
├── backend/
├── ai-service/
├── infra/
├── docs/
├── .github/
│   └── workflows/
├── docker-compose.yml
├── Makefile
├── AGENTS.md
└── README.md
```

## Bootstrap Steps

### Step 1 — Create repository root files
Create:
- `.gitignore`
- `README.md`
- `AGENTS.md`
- `docker-compose.yml`
- `Makefile`

### Step 2 — Create service folders
```bash
mkdir frontend backend ai-service infra docs .github .github/workflows
```

### Step 3 — Add a root README
Include:
- project summary
- tech stack
- how to run locally
- service map

### Step 4 — Add Docker compose for local development
Services:
- postgres
- redis
- backend
- frontend
- ai-service

### Step 5 — Add CI placeholder workflow
Minimum checks:
- backend build + test
- frontend lint + test
- ai-service test

## Human-owned Initial Commit
Create the first commit yourself before asking Codex to edit anything.

Suggested message:
```text
chore: initialize repo structure for MyTax Relief Vault MY
```

## Why this matters
Codex performs better when:
- the repo already exists
- the directory layout is clear
- commands are documented
- working conventions are explicit

## Bootstrap Success Criteria

- a reviewer can identify where backend, frontend, AI, infra, and docs work belong without guessing
- local development dependencies are declared in one obvious place
- CI placeholders make intended validation visible even if full pipelines are not ready
- `AGENTS.md` exists early enough to shape later tasks instead of being added as an afterthought
