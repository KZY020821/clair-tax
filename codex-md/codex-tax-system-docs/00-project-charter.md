# 00 — Project Charter

## Product Name
MyTax Relief Vault MY

## Objective
Build a production-grade personal tax management platform for Malaysians that helps users:
- log in with magic link authentication
- track tax-relief spending by year
- calculate estimated personal income tax
- upload and retain receipts for seven years
- receive AI-assisted optimization suggestions
- let admins manage yearly tax policy changes without code edits

## Agent Interpretation

- This charter is the highest-level product and architecture intent for the system docs in this folder.
- Treat the module list and architecture stack as the default baseline unless the repository or human owner has already chosen equivalent alternatives.
- Use this file to preserve scope and priorities when lower-level tasks are ambiguous.
- Do not treat this file as a request to build the full system in one pass. It defines the target state, not the task size.

## Primary Users
- Individual taxpayers in Malaysia
- Admin operators who maintain yearly tax rules
- Internal support users for audit and issue handling

## Recommended Architecture
- Frontend: Next.js + TypeScript
- Backend: Spring Boot + Java
- AI Service: FastAPI + Python
- Database: PostgreSQL
- Cache: Redis
- Object Storage: AWS S3
- Queue: AWS SQS
- Deployment: AWS ECS/Fargate + RDS + CloudFront

## Architectural Principles
1. Use a modular monolith first.
2. Treat tax rules as versioned data, not hardcoded logic.
3. Make receipt processing asynchronous.
4. Keep financial calculations deterministic and testable.
5. Make admin publishing auditable.
6. Keep stored documents immutable and retention-aware.
7. Make Codex work on narrow, reviewable tasks.

## Product Modules
- Authentication
- User profile and tax profile
- Policy engine
- Calculator engine
- Receipt vault
- OCR / extraction pipeline
- AI optimization
- Admin portal
- Reporting / audit

## Non-functional Requirements
- High reliability during tax season
- Low-latency read performance for public tax pages
- Strong auditability for changes in yearly policy
- Secure document storage and access control
- Easy maintainability year after year

## Decision Boundaries for Codex

- Do not reinterpret Malaysian tax law from scratch. Model and implement the rules that are explicitly provided by policy data and follow-up specs.
- Do not replace the recommended architecture with a different system shape unless a human explicitly asks for that tradeoff.
- Do not collapse admin, policy, calculator, and receipt concerns into one shared module just because they are related at the product level.
- When implementation details are missing, choose the narrowest option that preserves auditability, determinism, and future yearly policy updates.

## Delivery Strategy
### MVP
- Public static tax-relief pages for 2018–2025
- Magic link login
- User dashboard
- Tax calculator
- Admin policy management

### Phase 2
- Receipt upload and S3 retention
- OCR extraction pipeline
- Category cap consumption tracking

### Phase 3
- AI optimization suggestions
- Audit pack export
- Advanced admin workflows
