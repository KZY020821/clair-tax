# Clair Tax

Monorepo scaffold for a Malaysian personal tax management system.

## Services

- `frontend/` - Bun-managed Next.js App Router web app with TypeScript and TailwindCSS
- `backend/` - Spring Boot API scaffold using Java 21 and Maven
- `ai-service/` - FastAPI scaffold for OCR and AI orchestration
- `docs/` - repository-owned documentation
- `mobile-app/` - future mobile client workspace
- `infra/` - future infrastructure workspace

## Current scope

This repository currently includes scaffolding only:

- frontend app shell
- backend health endpoint
- AI service health endpoint
- setup documentation for each service

Authentication, tax logic, persistence flows, and containerization are intentionally not added yet.

## Prerequisites

- Bun 1.3+
- Java 21
- Python 3.11

## Start each service

### Frontend

```bash
cd frontend
bun install
bun run dev
```

Open `http://localhost:3000`.

If the backend is running on a different port during local verification, set:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:18080 bun run dev
```

If the AI service is running on a different port during local verification, set:

```bash
NEXT_PUBLIC_AI_SERVICE_BASE_URL=http://localhost:8001 bun run dev
```

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Open `http://localhost:8080/api/health` or `http://localhost:8080/actuator/health`.

The default `local` profile disables database auto-configuration so the backend can start before PostgreSQL and Flyway wiring are introduced.

To apply the PostgreSQL schema migration against a running local database:

```bash
cd backend
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

The demo integration endpoint is `GET /api/policy-years`.

### AI service

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Open `http://localhost:8000/health`.
The demo integration endpoint is `GET /api/demo-summary`.

## Service-specific guides

- [frontend/README.md](/Users/khorzeyi/Developer/clair-tax/frontend/README.md)
- [backend/README.md](/Users/khorzeyi/Developer/clair-tax/backend/README.md)
- [ai-service/README.md](/Users/khorzeyi/Developer/clair-tax/ai-service/README.md)
