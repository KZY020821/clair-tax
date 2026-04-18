# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Clair Tax is a Malaysian personal tax management system built as a monorepo with three main services: a Next.js web frontend, a Spring Boot backend API, and a FastAPI-based AI service for receipt processing and OCR.

## Quick Start Commands

### Frontend (Next.js + Bun)
```bash
cd frontend
bun install
bun run dev      # http://localhost:3000
bun run build
bun run lint
```

Environment variables:
- `NEXT_PUBLIC_API_BASE_URL` - Backend API URL (default: http://localhost:8080)
- `NEXT_PUBLIC_AI_SERVICE_BASE_URL` - AI service URL (default: http://localhost:8000)

### Backend (Spring Boot + Java 21)
```bash
cd backend
./mvnw spring-boot:run                              # local profile (no DB)
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run  # with PostgreSQL
./mvnw test
./mvnw compile                                      # compile only
```

Health endpoints: http://localhost:8080/api/health or http://localhost:8080/actuator/health

Environment variables:
- `SPRING_DATASOURCE_URL` - Database connection (default: jdbc:postgresql://localhost:5432/clair_tax)
- `SPRING_DATASOURCE_USERNAME` - Database user (default: postgres)
- `SPRING_DATASOURCE_PASSWORD` - Database password (default: postgres)
- `CLAIR_AUTH_PUBLIC_BASE_URL` - Public URL for magic links (e.g., http://<your-laptop-ip>:8080)
- `CLAIR_AUTH_MOBILE_APP_SCHEME` - Mobile app URL scheme (default: clair-tax)
- `SPRING_MAIL_USERNAME` - SMTP username for email delivery
- `SPRING_MAIL_PASSWORD` - SMTP password or app password
- `CLAIR_DEV_USER_EMAIL` - Override default dev email (default: dev@taxrelief.local)
- `CLAIR_RECEIPTS_STORAGE_PATH` - Local receipt storage path (default: /tmp/clair-tax-receipts)

### AI Service (FastAPI + Python 3.11)
```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000  # http://localhost:8000/health
pytest ai-service/tests
ruff check ai-service/app ai-service/tests
```

Environment variables (for production Lambda):
- `BACKEND_API_BASE_URL` - Backend API URL
- `BACKEND_INTERNAL_TOKEN` - Internal service token
- `AWS_REGION` - AWS region
- `DEFAULT_RECEIPT_CURRENCY` - Default currency code
- `TRAINED_RECEIPT_POSTPROCESSOR_ENABLED` - Enable trained model
- `TRAINED_RECEIPT_POSTPROCESSOR_ARTIFACT_PATH` - Path to .joblib model
- `RECEIPT_AMOUNT_SELECTION_THRESHOLD` - Confidence threshold for amount
- `RECEIPT_DATE_SELECTION_THRESHOLD` - Confidence threshold for date
- `RECEIPT_VALIDITY_THRESHOLD` - Overall validity threshold

## Architecture

### Backend: Spring Boot Service Layer Pattern

The backend uses a standard Spring Boot architecture with clear separation:

**Controller → Service → Repository → Entity**

- **Controllers** (`*Controller.java`): REST endpoints under `/api/*`, handles HTTP concerns, delegates to services
- **Services** (`*Service.java`): Business logic, transaction management, orchestration
- **Repositories** (`*Repository.java`): JPA data access, extends Spring Data interfaces
- **Entities** (`entity/*.java`): JPA entities mapped to PostgreSQL tables
- **DTOs** (`dto/*.java`): Request/response objects, validation with `@Valid` annotations

Key packages:
- `auth` - Magic link and OTP authentication, session management
- `calculator` - Tax calculation engine, reads policy data from database
- `policy` / `policyyear` - Policy year and relief category management
- `receipt` - Receipt CRUD, file upload/storage, extraction lifecycle
- `user` - User profile, marital status, disability, spouse, children
- `useryear` - Year workspace per user, manages relief claims and totals
- `reliefclaim` - Syncs claim totals when receipts are created/updated/deleted
- `suggestion` - Tax optimization suggestions based on profile and receipts
- `config` - Security (all endpoints permit all), CORS configuration

**Database schema**: Managed by Flyway migrations in `src/main/resources/db/migration/V*.sql`

**Current user resolution**: The backend uses a temporary local account fallback for development. In database-backed profiles (`!local`), `CurrentUserProvider` resolves either the authenticated session user or falls back to `dev@taxrelief.local`. All user-scoped endpoints (profile, receipts, year workspaces) filter by this resolved user ID.

**Authentication modes**:
- Web: Magic link flow via `/api/auth/magic-link/request` → `/api/auth/magic-link/verify` → session cookie
- Mobile: OTP flow via `/api/auth/otp/request` → `/api/auth/otp/verify` → session cookie
- Mobile magic link: Uses `/api/auth/mobile-link` bridge that redirects to `clair-tax://auth/verify?...`
- Dev fallback: `/api/dev/me` returns the temporary local account for testing

**Receipt storage**: Locally stored in `CLAIR_RECEIPTS_STORAGE_PATH` (default `/tmp/clair-tax-receipts`) in dev mode. Production uses S3 via `AwsReceiptObjectStorageService`.

**Tax calculation**: The calculator endpoint (`POST /api/calculator/calculate`) reads relief caps and progressive tax brackets from the selected policy year's database records. Household fields (spouse, disability, children) are resolved server-side from the saved profile, so reliefs are never trusted from frontend-only state.

### Frontend: Next.js App Router

The frontend uses Next.js App Router with TypeScript, TailwindCSS, TanStack Query, and Zod.

**Page structure**:
- `/` - Home with policy years and AI demo panel
- `/calculator` - Tax calculator with year selector, relief inputs, calculation result
- `/profile` - Profile settings (disability, marital status, spouse, children)
- `/year/create` - Create new year workspace
- `/year/[year]` - Year workspace with relief claim summary and receipt upload
- `/receipts` - All receipts across years
- `/receipts/[year]` - Receipts for specific year

**Key patterns**:
- All backend requests use `backendFetch()` from `lib/backend-api.ts` with `credentials: "include"` for session cookies
- TanStack Query handles all API state, caching, and refetching
- Relief category visibility is filtered client-side by profile using `lib/profile-relief-visibility.ts`
- Tax calculation is fully server-side; frontend only sends selected reliefs and displays results
- Receipt uploads use `multipart/form-data` with `FormData`

**Component organization**:
- `components/calculator/*` - Tax calculator UI
- `components/suggestion/*` - Dynamic suggestion cards
- `components/auth/*` - Login screen
- `components/profile/*` - Profile settings
- `components/year/*` - Year workspace UI
- `components/app-shell.tsx` - Main layout with navigation

### AI Service: FastAPI Receipt Processing

The AI service has two modes:
1. **Local development**: Exposes `/health` and `/api/demo-summary` for frontend integration
2. **Production**: Lambda SQS worker that processes receipt extraction jobs

**Processing flow** (production):
1. Backend queues receipt job to SQS
2. Lambda handler (`app.handlers.sqs.lambda_handler`) receives job
3. Downloads receipt file from S3
4. Runs OCR extraction via AWS Textract
5. Normalizes amount, date, merchant, currency, confidence
6. Optionally runs trained post-processor to re-score candidates and reject invalid receipts
7. Writes extraction result back to backend via internal API

**Training workflow**:
```bash
# 1. Generate annotation manifest from sample files
python -m app.cli.prepare_receipt_annotations --input-dir ./samples --output ./data/annotations.jsonl

# 2. Manually label the manifest with ground truth

# 3. Train model and export candidate tables
python -m app.cli.train_receipt_models --manifest ./data/labeled.jsonl --output ./model_artifacts/receipt_postprocessor.joblib --candidate-table-dir ./data/tables

# 4. Evaluate model on labeled data
python -m app.cli.evaluate_receipt_models --manifest ./data/labeled.jsonl --artifact ./model_artifacts/receipt_postprocessor.joblib
```

**Key modules**:
- `clients/*` - Backend API, S3 storage, Textract OCR clients
- `services/processing.py` - Main receipt processing orchestration
- `services/normalization.py` - Extract amount, date, merchant from OCR
- `services/postprocessing.py` - Trained model re-scoring and validation
- `services/training.py` - Model training and feature engineering
- `models/*` - Pydantic models for jobs, extraction, training data

## Database Schema Notes

**Policy years** (`policy_year`): Each year has status (draft/published/archived) and belongs to one-to-many relief categories

**Relief categories** (`relief_category`): Each category has name, description, `maxAmount`, `requiresReceipt`, and optional eligibility rules (`allowedMaritalStatus`, `requiresDisabledSelf`, etc.)

**Tax brackets** (`tax_bracket`): Progressive tax rates per policy year with `minIncome`, `maxIncome`, `rate`

**User profile** (`app_user`): Stores `isDisabled`, `maritalStatus`, `spouseDisabled`, `spouseWorking`, `hasChildren` for calculator eligibility

**Year workspaces** (`user_policy_year`): One per user per year, tracks when user creates a workspace

**Relief claims** (`user_relief_claim`): Aggregated claim totals per user per year per relief category, synced when receipts are added/updated/deleted

**Receipts** (`receipt`): User-uploaded receipts with `merchantName`, `receiptDate`, `amount`, `reliefCategoryId`, `fileName`, `fileUrl`, extraction status fields

**Magic links** (`magic_link_token`): Temporary tokens for email-based authentication

**OTP codes** (`email_otp_code`): Temporary codes for email OTP authentication with rate limiting

## Running Tests

Backend integration tests use Spring Boot Test with `@SpringBootTest` and `@Transactional` rollback. They run against an in-memory H2 database or test profile PostgreSQL.

```bash
cd backend
./mvnw test
./mvnw test -Dtest=TaxCalculatorControllerIntegrationTest  # single test class
```

## Common Development Workflows

### Adding a new relief category
1. Add database migration in `backend/src/main/resources/db/migration/V*__description.sql`
2. Eligibility rules are stored in relief_category columns (`allowedMaritalStatus`, `requiresDisabledSelf`, etc.)
3. Frontend filters categories using `isReliefEligible()` from `lib/profile-relief-visibility.ts`
4. Calculator service automatically applies eligible reliefs based on saved profile

### Adding a new tax bracket
1. Add migration to insert into `tax_bracket` table with `policyYearId`, `minIncome`, `maxIncome`, `rate`
2. Calculator service reads brackets dynamically from database

### Updating authentication flow
1. Backend auth controllers are in `auth/controller/AuthController.java`
2. Magic link service: `auth/service/MagicLinkAuthService.java`
3. OTP service: `auth/service/EmailOtpAuthService.java`
4. Session management: `auth/service/SessionCookieService.java`
5. Frontend login: `components/auth/login-screen.tsx`
6. SMTP mail templates: `backend/src/main/resources/mail/*.html` and `*.txt`

### Receipt extraction lifecycle
1. User uploads receipt via `/api/user-years/{year}/receipts`
2. Backend stores file locally or in S3, creates receipt with status `pending`
3. Backend queues job to SQS (production) or marks as queued
4. AI service processes job, extracts data, updates receipt status to `extracted` or `failed`
5. User reviews extracted data in year workspace

### Running full stack locally
```bash
# Terminal 1: PostgreSQL
# (start your local PostgreSQL instance)

# Terminal 2: Backend
cd backend
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run

# Terminal 3: Frontend
cd frontend
bun run dev

# Terminal 4: AI Service (optional for receipt extraction)
cd ai-service
source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Access at:
- Frontend: http://localhost:3000
- Backend: http://localhost:8080/api/health
- AI Service: http://localhost:8000/health

## Mobile App Notes

The `mobile-app/` directory has been deleted. Mobile functionality is now being reconsidered or may be integrated differently in the future. Historical git commits show it was an Expo-based React Native app.

## Key API Endpoints

All endpoints are under `/api/*`:

**Auth**: `/auth/magic-link/request`, `/auth/magic-link/verify`, `/auth/otp/request`, `/auth/otp/verify`, `/auth/session`, `/auth/logout`, `/auth/mobile-link`

**Calculator**: `POST /calculator/calculate`

**Policy**: `GET /policy-years`, `GET /policies/{year}`

**Profile**: `GET /profile`, `PUT /profile`, `DELETE /profile/account`

**Year Workspaces**: `GET /user-years`, `POST /user-years`, `GET /user-years/{year}`, `GET /user-years/{year}/receipts`, `POST /user-years/{year}/receipts`

**Receipts**: `GET /receipts/years`, `GET /receipts?year={year}`, `GET /receipts/{id}`, `GET /receipts/{id}/file`, `POST /receipts`, `PUT /receipts/{id}`, `DELETE /receipts/{id}`

**Suggestions**: `GET /suggestions?year={year}`

**Dev**: `GET /dev/me` (returns temporary local account for testing)

All user-scoped endpoints automatically filter by the current authenticated user or fall back to the temporary dev account in local mode.
