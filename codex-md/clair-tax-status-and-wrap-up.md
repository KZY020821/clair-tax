# Clair Tax Status and Wrap-Up

## Purpose

This file replaces the old planning packs under `codex-md/`.
It is the current Codex-ready source of truth for:

- what is already delivered in this repository
- what is actively in progress
- what still needs to be completed to wrap up the project cleanly

If repository code and this file ever diverge, trust the repository first and update this file.

## Current Delivery Snapshot

| Area | Status | Notes |
| --- | --- | --- |
| Frontend web app | Done | Next.js app is implemented and builds successfully with Bun. |
| Backend API | Done | Spring Boot API, calculator, profile, year workspace, and receipt review flows are implemented. |
| Mobile app | Done | Expo app is implemented and mirrors major web flows. |
| Database | Done | Flyway-backed schema and seeded policy data are in place. |
| FastAPI / AI service | Ongoing | Receipt extraction pipeline is implemented, but production completion and user-facing tax relief suggestion work remain. |
| S3 receipt backup configuration | Planned | Keep this in the wrap-up plan and do not mark as done yet. |

## Validation Snapshot

Validated through 2026-03-29:

- Backend: `cd backend && ./mvnw test` -> passed
- Backend: non-`local` `postgres` startup, Flyway migrate through `V11`, year workspace fetch, receipt listing, upload-intent, local relative upload URL `PUT`, confirm-upload, extraction writeback, confirm, reject, and claimed-total update -> passed
- AI service: `cd ai-service && ../ai-service/.venv/bin/ruff check app tests` -> passed
- AI service: `cd ai-service && ../ai-service/.venv/bin/pytest tests` -> passed
- Frontend: `cd frontend && bun run lint` -> passed
- Frontend: `cd frontend && bun run build` -> passed
- Mobile app: `cd mobile-app && pnpm typecheck` -> passed
- Mobile app: `cd mobile-app && bun run typecheck` -> passed after aligning the upload-intent receipt flow and rerunning after the non-`local` verification pass
- Mobile app: `cd mobile-app && EXPO_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 bun run ios:preflight` -> failed as expected on this machine because Xcode's active iOS Simulator SDK is `26.4` while the installed runtimes are `18.6`, `26.0`, and `26.2` only
- Backend: `cd backend && ./mvnw test` -> passed again after moving the magic-link email to a branded HTML template with plain-text fallback

Manual verification note:

- On 2026-03-28, the non-`local` backend/API receipt flow was verified end to end for the mobile contract using one PDF-like upload and one image-like upload: upload intent creation, relative local upload URL handling, confirm-upload, processing writeback, extraction writeback, `processed` receipt payloads, confirm, reject, and claimed-total updates.
- On 2026-03-28, a follow-up iPhone Simulator build pass fixed the earlier CocoaPods prebuilt React Native failure by aligning the mobile app to Expo SDK 54's recommended `react-native` patch line, adding an explicit iOS bundle identifier, and forcing local iOS prebuilds to build React Native from source instead of using the `ReactNativeDependencies` Maven tarball path.
- On 2026-03-28, the repo gained `bun run ios:preflight` and `bun run ios:sim` so the mobile iPhone Simulator path now checks Xcode SDK/runtime alignment and selects a concrete iPhone simulator UDID before Expo starts.
- On 2026-03-29, localhost web magic-link delivery was upgraded from plain text to a frontend-aligned HTML email with plain-text fallback, using the same black, white, and blue brand system as the web auth screen.
- Remaining gap: the final mobile UI-only smoke pass for the document picker and camera entry points is still open because this machine still lacks the required iOS `26.4` simulator runtime. The next external prerequisite is to install iOS `26.4` from `Xcode > Settings > Components`, rerun `bun run ios:preflight`, then use `bun run ios:sim` before retrying the receipt UI smoke pass.
- Manual inbox rendering confirmation for the branded magic-link email is still pending. Backend test coverage now verifies the multipart HTML plus plain-text mail shape and verify URL content.

Important repository note:

- The frontend workspace is Bun-managed, and CI now uses Bun for frontend install, lint, and build steps.
- The frontend package still does not expose a `test` script; automated UI coverage remains a separate follow-up item.
- The AI service CI install step now uses the tracked `requirements.txt` file only.

## Legacy Codex Docs, Interpreted

The removed folders covered these milestone themes:

1. Core platform foundation
2. Authentication
3. Tax policy engine
4. Tax calculator
5. Receipt ingestion
6. AI extraction
7. Frontend app
8. Admin and operations
9. QA, deployment, and release
10. Mobile parity, receipt capture, notifications, and release

Repository reality today:

- Core platform foundation is done
- Tax policy engine is done
- Tax calculator is done
- Frontend web app is done
- Mobile app is done
- Database foundation is done
- Receipt ingestion is mostly done
- AI extraction is in progress
- Production storage and release hardening are still open

## Completed Work in Detail

### 1. Monorepo foundation

- [x] Repository is split into `frontend/`, `backend/`, `ai-service/`, `mobile-app/`, `infra/`, and `docs/`.
- [x] Root `AGENTS.md` establishes service boundaries and repo operating rules.
- [x] Service-specific README files exist for backend, frontend, mobile app, AI service, and infra.
- [x] CI workflow exists for backend, frontend, and AI service lanes.

### 2. Backend API and database

- [x] Spring Boot modular monolith is established under `backend/`.
- [x] Flyway migrations exist through `V11`, covering tax data, seeded historical policy years, saved profile fields, user policy year workspaces, receipt ingestion lifecycle, training feedback support, and receipt-status storage normalization for non-`local` PostgreSQL usage.
- [x] Policy year API is implemented.
- [x] Policy detail API is implemented and returns backend-owned relief categories.
- [x] Tax calculator API is implemented with backend-owned rules and profile-aware relief handling.
- [x] Profile API is implemented for the current user.
- [x] User year workspace API is implemented.
- [x] Receipt CRUD and receipt review APIs are implemented.
- [x] Receipt upload intent and confirm-upload flow are implemented for object-storage-first uploads.
- [x] Internal receipt ingestion endpoints are implemented for AI writeback and reviewed training export.
- [x] Local storage implementation exists for development and test usage.
- [x] AWS S3 storage implementation exists for non-local profiles.
- [x] AWS SQS job publisher implementation exists for non-local profiles.
- [x] Backend integration tests are present for calculator, policy, profile, dev user, receipt, and user year flows.
- [x] Non-`local` receipt lifecycle drift uncovered during mobile verification is repaired.
  - `V9` now restores the receipt lifecycle columns needed for object-storage-first uploads on a fresh PostgreSQL schema.
  - `V11` normalizes receipt status storage so PostgreSQL-backed receipt listing works with the JPA enum mapping.

### 3. Frontend web application

- [x] Next.js App Router application is implemented under `frontend/app`.
- [x] Shared application shell, dashboard, calculator, profile, receipts, and year workspace routes are implemented.
- [x] TanStack Query is used for server state.
- [x] Zod-backed client parsing is implemented for backend and AI responses.
- [x] Web receipt upload uses the backend upload-intent plus confirm-upload flow.
- [x] Web year workspace includes receipt review, verification, rejection, and profile-aware category visibility.
- [x] Styling tokens, shell layout, and brand palette are established in global frontend styling.
- [x] Frontend lint and production build pass with Bun.

### 4. Mobile application

- [x] Expo Router app is implemented under `mobile-app/app`.
- [x] Mobile dashboard, calculator, profile, and year workspace screens exist.
- [x] Shared mobile UI primitives and theme tokens are implemented.
- [x] TanStack Query is used for mobile server state.
- [x] Mobile host detection for local backend and AI service connectivity is implemented.
- [x] Mobile receipt capture supports camera capture, image selection, and document picking.
- [x] Mobile receipt upload code now uses the backend upload-intent plus confirm-upload flow, matching the web client contract.
- [x] Mobile type checking passes.

### 5. AI service foundation

- [x] FastAPI app exists and exposes `/health` and `/api/demo-summary`.
- [x] SQS Lambda batch handler exists for receipt-processing jobs.
- [x] S3 client, backend writeback client, and Textract client are implemented.
- [x] Receipt processing service is implemented with retriable vs non-retriable failure handling.
- [x] Receipt normalization and post-processing logic are implemented.
- [x] Optional trained post-processor support is implemented.
- [x] CLI workflows exist for annotation preparation, model training, and evaluation.
- [x] AI service unit and workflow tests are present and passing.

### 6. Infrastructure foundation

- [x] Terraform scaffolding exists for the AI Lambda worker, SQS queue, DLQ, IAM, and CloudWatch alarm.
- [x] Environment folders exist for `dev`, `staging`, and `prod`.

## Ongoing Work

### FastAPI / AI extraction

- [~] Receipt extraction is implemented at the pipeline level, but production completion still needs environment wiring, deployment completion, and end-to-end verification with real S3 plus SQS inputs.
- [~] User-facing AI suggestions for maximizing tax relief are not finished yet. Current frontend and mobile dashboards still surface static suggestion cards plus a demo AI summary, not a real recommendation engine.

### Planned but not done yet

- [ ] S3 receipt backup configuration should be completed as part of the wrap-up plan.

## Wrap-Up Task List

Use this backlog to close the project in a senior-engineering order. Complete the `P0` items before calling the platform launch-ready.

### P0 - Release blockers

- [ ] Complete production S3 receipt backup configuration.
  - Provision bucket(s) and environment-specific names.
  - Confirm server-side encryption, IAM access, upload intent flow, object retrieval, and deletion behavior.
  - Verify backend non-local profile behavior against real AWS resources.
  - Add lifecycle and retention rules for receipt files and backups.

- [ ] Finish the FastAPI extraction path end to end in production shape.
  - Build and publish the Lambda image or deployment artifact.
  - Verify SQS trigger, S3 object reads, Textract invocation, backend writeback, and DLQ behavior.
  - Resolve environment and secret wiring gaps between Terraform and application settings before production rollout.

- [ ] Deliver real AI tax relief suggestions.
  - Define the source of truth for suggestion inputs.
  - Decide whether suggestion orchestration lives in backend, AI service, or a hybrid flow.
  - Expose a real API contract for suggestion results.
  - Render explainable suggestions in web and mobile without bypassing backend-owned tax rules.
  - Keep all suggestions overrideable by the user.

- [ ] Reconcile mobile receipt upload with the current backend contract.
  - Mobile code now uses the same upload-intent plus confirm-upload flow as the web app.
  - A non-`local` backend/API verification pass completed on 2026-03-28 for upload intent creation, relative upload URL handling, confirm-upload, extraction writeback, confirm, reject, and claimed-total updates.
  - Remaining work: run one on-device or emulator mobile UI smoke pass for the document picker and camera capture entry points before closing this item.
  - Current blocker: the repo now provides `bun run ios:preflight` and `bun run ios:sim` for the iPhone Simulator path, but this machine still lacks the exact iOS `26.4` simulator runtime required by Xcode `26.4`. Install that runtime from `Xcode > Settings > Components`, rerun the preflight, and then complete the last receipt UI smoke pass.

### P1 - Validation and release hardening

- [x] Fix package-manager and CI drift.
  - Frontend CI now uses Bun to install dependencies and run lint plus build, matching the checked-in lockfile and package manager declaration.
  - The stale frontend `test` expectation was removed instead of introducing a placeholder test harness.
  - AI service CI now installs tracked dependencies from `requirements.txt` only.

- [ ] Add missing automated coverage around user-facing flows.
  - Frontend: calculator, profile, year workspace, and receipt upload/review states.
  - Mobile: core screen logic, receipt upload flow, and API contract parsing.
  - End-to-end: upload intent -> file upload -> AI extraction -> receipt review -> claimed total update.

- [ ] Close production auth and security decisions.
  - Localhost web magic-link auth is now implemented with backend session lookup, email-link verification, branded HTML email delivery with plain-text fallback, cookie-backed browser persistence, logout, and cross-tab sync.
  - Remaining auth work is to extend or adapt that flow for mobile and other production-facing clients instead of leaving them on the temporary local-account fallback.
  - Replace the dev-only current-user mode as the release path if the product is going live.
  - Define final magic link issuance, verification, session persistence, and logout behavior across all clients and environments.
  - Review internal token handling, secret storage, and environment variable policy.
  - Confirm auditability for sensitive receipt and profile actions.

- [ ] Finalize deployment and runbook documentation.
  - Backend, frontend, AI service, and infra deploy order.
  - Required environment variables and secret locations.
  - Failure recovery steps for stuck jobs, failed extraction, and storage issues.

### P2 - Operational polish

- [ ] Add monitoring and alerting beyond the current Lambda DLQ alarm.
  - Backend API errors
  - AI writeback failures
  - Receipt-processing backlog depth
  - Storage failures and missing-object cases

- [ ] Write repository-owned docs in `docs/` for architecture, deployment, and operations.

- [ ] Prepare a short launch checklist for final manual QA across web, mobile, backend, and AI processing.

## Recommended Execution Order

1. Lock down S3 storage configuration and environment wiring.
2. Complete AI extraction deployment and run a real end-to-end receipt-processing test.
3. Align mobile receipt upload with the backend upload contract.
4. Deliver the real AI suggestion capability for maximizing tax relief.
5. Integrate magic link authorization and replace the dev-only current-user path in production-facing flows.
6. Repair CI and expand test coverage around the authenticated product flows.
7. Finalize secrets, runbooks, launch QA, and operational monitoring.

## Definition of Project Completion

Only mark the project fully wrapped up when all of the following are true:

- Web, backend, mobile, and database remain green after final validation.
- FastAPI receipt extraction is fully wired and verified in production-like conditions.
- AI suggestions for maximizing tax relief are real, explainable, and user-overrideable.
- S3 receipt backup configuration is complete and verified.
- Magic link authorization is implemented for production-facing access and the dev-only current-user shortcut is no longer the release path.
- CI matches the actual package managers and scripts used by the repo.
- Security and deployment decisions are documented and tested.

## Codex Working Notes

When future Codex sessions use this repository:

- treat this file as the replacement for the deleted legacy planning packs
- prefer repository code and tests over stale planning assumptions
- keep tax rules data-driven in backend-owned policy data
- keep AI suggestions advisory only, with user correction always taking precedence
