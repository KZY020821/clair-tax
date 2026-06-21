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
- [x] **Phase 1 — CI (GitHub Actions, no cloud):** lint + build + test per service, path-filtered. DONE.
  - [x] Frontend (`frontend-ci.yml`) — merged (#2)
  - [x] Backend (`backend-ci.yml`) — merged (#3, fixed WIP endpoint to go green)
  - [x] AI service (`ai-service-ci.yml`) — merged (#4, lean ML-free CI)
  - [x] Branch protection on `main` requiring the 3 checks
  - [ ] (later) Aggregator "CI Gate" job to fix the path-filter required-check caveat
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
  needed). PR #3.
- **Backend CI first ran RED — CI correctly caught unfinished WIP.** 3 tests
  failed with 404 on `POST /api/user-years/{year}/receipts/upload-intent`. Root
  cause: the WIP commit built the full upload-intent flow (DTOs, entity,
  repository, migrations V9/V10, `ReceiptService.createUploadIntent` +
  `confirmUpload`, and the test harness) but never wired the two HTTP endpoints
  in `UserYearController`. Fix: added `POST .../upload-intent` and
  `POST .../confirm-upload` handlers delegating to the existing service methods.
  Verified green locally (`UserYearControllerIntegrationTest` 5/5,
  `ProfileControllerIntegrationTest` 3/3). Squash-merged (#3, commit `4aafd7b`).
- Authored **`.github/workflows/ai-service-ci.yml`** (checkout → setup Python
  3.11 with pip cache → install `requirements-ci.txt` → `ruff check app tests`
  → `pytest tests/unit`), path-filtered to `ai-service/**`.
- Added **`ai-service/requirements-ci.txt`** — a lightweight dependency set that
  EXCLUDES the heavy ML stack (easyocr/torch/transformers/scikit-learn/PyMuPDF).
  Works because `app/clients/ocr.py` imports easyocr lazily and the OCR unit
  tests mock `get_ocr_reader`, so the heavy libs are never loaded. Keeps the
  ai-service pipeline fast (seconds, not minutes).
- Cleaned 56 pre-existing `ruff` violations (55 via `ruff check --fix` —
  whitespace + unused imports; 1 unused `with ... as` binding by hand).
- Verified in a fresh throwaway venv: lean install + `ruff` clean + 61 unit
  tests pass. Integration tests (`tests/integration`) deferred — they need the
  full ML stack / AWS mocking and are a later enhancement.

- **Branch protection enabled on `main`** (via `gh api PUT .../branches/main/protection`):
  require a PR before merging + require the 3 checks (`build-and-lint`, `test`,
  `lint-and-test`); `enforce_admins=false`; 0 required approvals (solo repo).
  Known caveat: path-filtered workflows mean a PR not touching a service leaves
  that service's check unreported ("Expected — waiting"); admin override is used
  to merge such PRs. Proper fix for later = a single aggregator "CI Gate" job.

Decisions:
- **GitHub Actions** chosen as CI tool (already on GitHub, free, config-in-repo).
- **Per-service path-filtered workflows** so a change in one service doesn't run
  the others' pipelines.
- CI work is developed on branch **`ci/frontend-pipeline`** via pull request
  (not committed straight to `main`) to practice the professional PR + checks flow.
- `.mcp.json` intentionally left untracked (local tooling config).

## 6. Next action

- Merge the ai-service CI PR to finish **Phase 1** (all 3 services have CI).
- Optional Phase 1 polish: add **branch protection** on `main` requiring the CI
  checks to pass before merge (the real safety payoff of CI). Bump
  `actions/checkout`/`setup-java` to avoid the Node 20 deprecation warning.
  Consider adding `ai-service` integration tests later (needs full ML stack).
- Then begin **Phase 2 — Cloud foundations (Terraform)**: AWS account + Budgets
  alerts first, then VPC, ECR, S3, Secrets Manager.

---

*Keep this file current: update §5 and §6 after each step.*
