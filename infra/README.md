# Infrastructure

Terraform scaffolding for the Lambda-based AI receipt extraction worker lives here.

## Layout

- `modules/ai-service-lambda`: reusable Lambda, SQS, DLQ, IAM, and CloudWatch resources
- `envs/dev`
- `envs/staging`
- `envs/prod`
