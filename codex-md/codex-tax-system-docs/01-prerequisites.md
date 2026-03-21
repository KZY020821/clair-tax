# 01 — Prerequisites

## Accounts You Need
- GitHub account
- ChatGPT account with access to Codex
- OpenAI developer/account access if you also use API-based Codex CLI workflows
- AWS account
- Domain / DNS provider account
- Email provider account for magic links

## Agent Setup Notes

- This file describes environmental readiness owned primarily by the human operator, not work that Codex should silently invent around.
- Missing accounts, missing credentials, or missing local runtimes should be treated as blockers or explicit assumptions, not ignored.
- Example environment variables are placeholders. Preserve the names and intent, but keep generated values, secrets, and provider-specific identifiers out of version control.
- If the repository already uses different package managers, wrappers, or directory names, adapt the commands while keeping the same operational outcomes.

## Local Tools
Install these before touching Codex.

### Core
- Git
- Docker Desktop
- Node.js LTS
- Java 21
- Maven or project wrapper
- Python 3.11+
- PostgreSQL client tools

### Recommended extras
- VS Code or IntelliJ IDEA
- AWS CLI
- pnpm or npm
- Makefile support

## Local Environment Variables
Create local `.env` files but never commit secrets.

### frontend/.env.local
```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

### backend/.env
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=mytax
DB_USER=mytax
DB_PASSWORD=mytax
JWT_SECRET=replace_me
MAGIC_LINK_SECRET=replace_me
AWS_REGION=ap-southeast-1
S3_BUCKET=mytax-dev-receipts
SQS_RECEIPT_QUEUE_URL=replace_me
REDIS_URL=redis://localhost:6379
EMAIL_FROM=no-reply@example.com
SMTP_HOST=replace_me
SMTP_PORT=587
SMTP_USER=replace_me
SMTP_PASSWORD=replace_me
```

### ai-service/.env
```env
DATABASE_URL=postgresql://mytax:mytax@localhost:5432/mytax
AWS_REGION=ap-southeast-1
S3_BUCKET=mytax-dev-receipts
```

## Git Strategy
Use these branches:
- `main` — production-ready
- `develop` — integration branch
- `feature/*` — Codex task branches

## First Human Tasks
Before Codex starts:
1. Create an empty GitHub repo.
2. Decide organization name.
3. Decide package names.
4. Decide primary AWS region.
5. Decide local port conventions.
6. Decide auth email provider.
7. Decide naming convention for environments: `dev`, `staging`, `prod`.

## Readiness Signal for Codex

Codex should assume the workspace is ready only when:
- the repository exists
- service folder names are decided
- local commands are known
- environment variable placeholders are documented
- the human has chosen unresolved naming and hosting conventions
