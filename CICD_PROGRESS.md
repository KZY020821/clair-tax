# Clair Tax — CI/CD & Cloud Deployment Progress

> **Purpose of this document:** This file is the single source of truth for the
> CI/CD and cloud-deployment learning effort on Clair Tax. If a chat session
> grows too long, **attach this file to a new session** to resume with full
> context. Keep it updated after every meaningful step.

---

## 1. Goal & context

**Who:** The repo owner is learning CI/CD and cloud architecture **hands-on**,
as an absolute beginner, using this real project. Teaching depth chosen:
**"explain every command"** — go slow, explain the *why* behind each file and
command line by line.

**What we're building toward:**
1. A full **CI/CD pipeline** (GitHub Actions) for the 3 services in this monorepo.
2. A **cost-effective AWS cloud architecture** suited to the traffic profile of a
   Malaysian income-tax app: near-idle ~10 months/year, then **extreme spikes in
   March–April** (LHDN e-Filing season). Principle: *pay near-zero when idle,
   auto-scale hard during tax season, scale back down automatically.*

---

## 2. Repository facts (ground truth)

- **GitHub repo:** `KZY020821/clair-tax`  •  **Default branch:** `main`
- **Monorepo, 3 deployable services:**

| Service | Folder | Stack | Lint | Test | Build |
|---------|--------|-------|------|------|-------|
| Frontend | `frontend/` | Next.js 16 + Bun 1.3.8 + TS | `bun run lint` (eslint) | _(none yet)_ | `bun run build` |
| Backend | `backend/` | Spring Boot 3.4.4, Java 21, Maven | _(none yet)_ | `./mvnw test` (10 test files, H2) | `./mvnw package` |
| AI service | `ai-service/` | FastAPI, Python 3.11 | `ruff check` (has `ruff.toml`) | `pytest` (unit + integration) | Docker (`ai-service/Dockerfile`) |

- **Heavy dep warning:** `ai-service` uses `easyocr` (large ML deps) and keeps a
  ~1.5 GB local model cache at `ai-service/.hf_cache/` — now gitignored. CI for
  this service must avoid downloading the full ML stack where possible (run
  `ruff` + lightweight unit tests; mock/skip OCR-heavy paths).
- **Existing infra dirs:** `infra/` and `aws/` exist in the repo (not yet
  reviewed in this effort).

---

## 3. Target cloud architecture (agreed design)

Region: **`ap-southeast-5` (AWS Kuala Lumpur)** for PDPA data residency + low
latency (fallback: `ap-southeast-1` Singapore).

| Layer | Service | Why |
|-------|---------|-----|
| Edge | Route 53 + CloudFront + WAF | DNS, CDN caching (absorbs read spikes), firewall/rate-limit |
| Frontend | AWS Amplify Hosting (Next.js) | Auto-scales, minimal ops |
| API edge | Application Load Balancer (ALB) | Spreads traffic across backend containers |
| Backend | Spring Boot on **ECS Fargate** + auto-scaling (1 → N tasks) | No server mgmt; avoids JVM cold-start pain of Lambda; scales with demand |
| Database | **Aurora Serverless v2** (PostgreSQL) | Capacity scales up/down with load; cheap baseline off-season |
| Receipts | S3 → SQS → **Lambda** (FastAPI AI) → Textract | Async, bursty, pay-per-use; already designed this way in code |
| Registry | ECR | Stores Docker images |
| Secrets | AWS Secrets Manager | DB creds, API keys injected at runtime |
| Cost guardrails | AWS Budgets + Cost Explorer + scaling caps | Prevent surprise bills |

---

## 4. Roadmap (phases)

- [ ] **Phase 0 — Dockerize:** confirm/author Dockerfiles for backend & ai-service; run locally.
- [~] **Phase 1 — CI (GitHub Actions, no cloud):** lint + build + test per service, path-filtered.
  - [x] Frontend (`frontend-ci.yml`) — merged
  - [~] Backend (`backend-ci.yml`) — PR open
  - [ ] AI service (`ai-service-ci.yml`)
- [ ] **Phase 2 — Cloud foundations (Terraform):** AWS account, Budgets, VPC, ECR, S3, Secrets Manager.
- [ ] **Phase 3 — CD to staging:** build image → ECR → deploy Fargate; Aurora/RDS; Amplify; Lambda+SQS; smoke tests.
- [ ] **Phase 4 — Production + manual approval gate:** duplicate env via Terraform; auto-scaling; CloudFront+WAF.
- [ ] **Phase 5 — Production polish:** observability (CloudWatch), rollback / blue-green, load test (k6) before April.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done

---

## 5. Current status & decisions log

**As of latest update — working in Phase 1 (CI).**

Done:
- Committed pre-existing WIP "AI chat assistant integration" feature to `main`
  (commit `e4b4102`) to get a clean working tree before starting CI work.
- Added `ai-service/.hf_cache/` to `.gitignore` (1.5 GB model cache must never
  be committed; exceeds GitHub's 100 MB file limit).
- Authored first CI workflow: **`.github/workflows/frontend-ci.yml`**
  (checkout → install Bun 1.3.8 → `bun install --frozen-lockfile` → `bun run
  lint` → `bun run build`), path-filtered to `frontend/**`. Triggers on push to
  `main` and on pull requests.
- **Frontend CI is GREEN and MERGED.** PR #2 (`ci/frontend-pipeline` → `main`)
  passed and was squash-merged (commit `2f6d3d4`). Branch deleted.
- Authored **`.github/workflows/backend-ci.yml`** (checkout → setup Temurin
  Java 21 with maven cache → `./mvnw -B -ntp test`), path-filtered to
  `backend/**`. Note: repo uses a custom `mvnw` that uses the committed Maven
  3.9.9 dist or downloads it — works on clean CI runners. Tests run on H2 (no DB
  needed). Pushed on branch `ci/backend-pipeline` to watch via PR.

Decisions:
- **GitHub Actions** chosen as CI tool (already on GitHub, free, config-in-repo).
- **Per-service path-filtered workflows** so a change in one service doesn't run
  the others' pipelines.
- CI work is developed on branch **`ci/frontend-pipeline`** via pull request
  (not committed straight to `main`) to practice the professional PR + checks flow.
- `.mcp.json` intentionally left untracked (local tooling config).

## 6. Next action

- Push branch `ci/frontend-pipeline` with `frontend-ci.yml`, open a PR, and watch
  the GitHub Actions run. Read logs together if red.
- Then add `backend-ci.yml` (`./mvnw test`) and `ai-service-ci.yml`
  (`ruff` + light `pytest`).

---

*Keep this file current: update §5 and §6 after each step.*
