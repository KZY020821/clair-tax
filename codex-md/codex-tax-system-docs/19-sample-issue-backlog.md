# 19 — Sample Issue Backlog

## Agent Backlog Interpretation

- Treat each bullet as a candidate ticket seed, not as a guarantee that multiple bullets belong in one PR.
- Split schema work, API contracts, UI work, and infrastructure changes when they are tightly coupled enough to make review difficult.
- Preserve epic order unless the repository already has a different dependency graph that is clearly safer or more mature.

## Epic 1 — Repo and Tooling
- initialize repo structure
- add AGENTS.md
- add docker compose
- add CI skeleton

## Epic 2 — Backend Foundation
- scaffold Spring Boot app
- add PostgreSQL config
- add Flyway setup
- add health endpoint
- add exception handling

## Epic 3 — Authentication
- add user table
- add magic link table
- implement request link API
- implement verify link API
- add session token support

## Epic 4 — Policy Engine
- add policy year tables
- add bracket tables
- add relief category tables
- add clone workflow
- add publish workflow

## Epic 5 — Calculator
- add calculator service
- add estimate API
- add save profile API
- add tests for yearly bracket math

## Epic 6 — Receipt Vault
- add receipt metadata tables
- add upload intent API
- add S3 integration abstraction
- add queue dispatch
- add review endpoints

## Epic 7 — AI Service
- scaffold FastAPI service
- add provider abstraction
- add worker skeleton
- add normalization tests

## Epic 8 — Frontend
- scaffold Next.js app
- add auth pages
- add dashboard
- add calculator page
- add receipt pages

## Epic 9 — Admin Portal
- add policy list
- add bracket editor
- add relief editor
- add publish screen
- add audit log screen

## Epic 10 — Release Readiness
- add monitoring hooks
- add smoke tests
- add staging deploy pipeline
- production hardening review

## Usage Note

- Pair backlog items with the phase documents and prompt library so the ticket has both scope and delivery constraints.
- When a ticket crosses multiple epics, define the dependency boundary explicitly before asking Codex to implement it.
