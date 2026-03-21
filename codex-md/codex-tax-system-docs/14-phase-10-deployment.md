# 14 — Phase 10: Deployment

## Objective
Deploy the platform reliably across dev, staging, and production.

## Agent Interpretation

- This phase establishes environment separation, repeatable infrastructure definitions, and safe deployment structure.
- Prefer explicit placeholders and documented variables over guessed production settings.
- Infrastructure scaffolding should be conservative by default so later hardening can happen without first undoing risky defaults.

## Environments
- dev
- staging
- prod

## AWS Services
- ECS/Fargate for backend and AI service
- RDS PostgreSQL
- ElastiCache Redis
- S3 for receipts and static artifacts
- CloudFront for public content
- SQS for receipt jobs
- Secrets Manager / Parameter Store for secrets
- CloudWatch for logs and metrics

## Infra Structure
```text
infra/
├── modules/
├── envs/
│   ├── dev/
│   ├── staging/
│   └── prod/
└── README.md
```

## Deployment Order
1. Provision networking
2. Provision RDS and Redis
3. Provision S3 buckets and lifecycle policies
4. Provision SQS queues and DLQ
5. Deploy backend
6. Deploy ai-service
7. Deploy frontend
8. Run smoke tests

## Codex Task 1
```md
Create infrastructure scaffolding.

Requirements:
- add infra folder structure
- add placeholder Terraform modules or IaC skeleton for RDS, S3, SQS, ECS services
- document expected variables

Constraints:
- no real secrets
- no destructive defaults
```

## Codex Task 2
```md
Add CI/CD workflow skeleton.

Requirements:
- build backend, frontend, ai-service
- run tests
- package artifacts
- add deploy job placeholders for staging and prod
```

## Review Checklist
- environments clearly separated
- receipt bucket protected
- queue has DLQ
- secrets externalized

## Dependencies and Handoff

- Depends on agreed environment naming, service build outputs, and infrastructure ownership conventions.
- Should unblock secure staging and production rollout without forcing application teams to improvise environment setup.
- Human review should focus on blast radius, environment isolation, secret handling, and operational rollback paths.
