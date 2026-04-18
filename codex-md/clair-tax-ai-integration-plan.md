# Clair Tax AI Integration Plan

## Purpose

This document is the canonical implementation plan for adding AI to Clair Tax.
It is grounded in the current repository structure and existing delivery state:

- `backend/` remains the source of truth for Malaysian tax policy, calculator logic, user state, auth, receipts, and public APIs
- `ai-service/` remains the Python-native AI, OCR, retrieval, orchestration, training, and evaluation service
- `frontend/` and `mobile-app/` remain thin clients over backend-owned contracts
- `infra/` continues to own AWS deployment resources

If repository code and this document diverge, trust the repository first and update this file.

## Main Objectives

Clair Tax needs three AI capabilities:

1. Receipt intelligence
   - Extract receipt fields such as amount and date from uploaded files
   - Improve extraction quality over time from reviewed receipts
   - Suggest likely tax-relief categories without auto-filing claims

2. Tax recommendation agent
   - Understand Malaysian tax reliefs by year of assessment
   - Give grounded, year-aware suggestions such as PRS contribution guidance
   - Use backend-owned policy and calculator tools instead of inventing numbers

3. Tax chat copilot
   - Answer Malaysian tax questions in plain language
   - Explain how to use the Clair Tax platform
   - Work with receipts, categories, year summaries, and user context
   - Cite sources and refuse to guess when support is missing

## Repo Reality And Design Constraints

The current repo already provides the base needed for this plan:

- Spring Boot already owns:
  - policy years
  - relief categories
  - profile-aware calculator logic
  - saved user-year workspaces
  - receipt upload, extraction lifecycle, review, and training-export support
- FastAPI already owns:
  - receipt OCR orchestration
  - Textract integration
  - SQS/Lambda processing
  - receipt postprocessing
  - receipt model training and evaluation scripts
- Web and mobile already share:
  - typed receipt contracts
  - AI demo summary hooks
  - backend-first integration patterns
- Terraform already provisions:
  - Lambda-based AI worker infrastructure
  - SQS queue and DLQ
  - baseline IAM and CloudWatch resources

The design must preserve these rules:

- Do not hardcode tax rules into frontend, mobile, or free-form AI prompts
- Do not let the model answer tax-cap questions from memory alone
- Do not bypass backend auth or backend-owned tax calculations
- Do not auto-apply receipt category suggestions without user confirmation

## Recommended Technical Stack

### Core Services

- Public API and tax truth: Java 21 + Spring Boot
- AI orchestration and training: Python 3.11 + FastAPI + Pydantic
- Web UI: Next.js App Router + TypeScript
- Mobile UI: Expo + React Native + TypeScript
- Primary database: PostgreSQL
- Object storage: AWS S3
- Batch queue: AWS SQS

### AI Runtime

- Agent workflow orchestration: LangGraph in `ai-service`
- Embeddings: `intfloat/multilingual-e5-large-instruct`
- Retrieval store: PostgreSQL with `pgvector`
- Baseline chat model: `Qwen/Qwen2.5-7B-Instruct`
- Interactive model serving: vLLM on an AWS ECS GPU service
- Batch OCR extraction: keep AWS Textract `AnalyzeExpense`
- Vision fallback experimentation: `microsoft/Phi-3.5-vision-instruct` or similar document/vision models
- Fine-tuning workflow: Hugging Face Jobs + PEFT/LoRA + TRL

### Why This Split

- Spring Boot is already the natural home of tax truth and user-facing APIs
- FastAPI is already the natural home of OCR, training, and AI-side orchestration
- Lambda is a good fit for batch extraction but not a good fit for interactive, always-on chat inference
- PostgreSQL is already central to the platform, so `pgvector` is the lowest-friction retrieval option

## Target Architecture

### Service Ownership

#### Backend

Backend remains responsible for:

- public AI endpoints
- authentication and authorization
- user scoping
- tax policy retrieval
- calculator simulation
- profile-aware eligibility logic
- receipt persistence
- auditability and response history persistence

#### AI Service

AI service becomes responsible for:

- retrieval pipeline
- knowledge ingestion and chunking
- embeddings generation
- conversation orchestration
- tool-calling logic
- receipt categorization suggestions
- merchant normalization
- offline evaluation
- training and experiment jobs

#### Frontend And Mobile

Clients remain responsible for:

- rendering AI responses
- conversation UI
- receipt insight UI
- loading, empty, success, and error states
- streaming UX
- user confirmation and correction flows

### Runtime Topology

- Backend remains the public system of entry
- Backend calls internal AI service endpoints for AI orchestration
- AI service calls backend internal tools for policy, calculator, receipt, and user context
- Batch receipt extraction stays on SQS + Lambda
- Interactive chat and retrieval run on a dedicated GPU-backed ECS service

## Functional Scope

### A. Receipt Intelligence

#### v1 outcomes

- keep current upload-intent -> S3 -> SQS -> Lambda -> Textract -> postprocessing -> review pipeline
- improve trained amount/date validity postprocessor
- add merchant normalization
- add relief-category suggestion for reviewed receipts
- add receipt summary generation for chat and year workspace use

#### v1 behavior rules

- AI may suggest:
  - merchant
  - amount
  - date
  - currency
  - likely relief category
  - short receipt summary
- AI may not:
  - confirm a receipt review on behalf of the user
  - update relief claims without explicit user action
  - silently override a reviewed receipt

### B. Tax Recommendation Agent

#### v1 outcomes

- answer year-aware Malaysian tax questions from curated official materials only
- explain remaining relief opportunities using backend tool results
- support recommendation flows such as:
  - PRS contribution guidance
  - category cap reminders
  - missing-receipt nudges
  - next-best-action suggestions

#### v1 behavior rules

- backend computes caps, remaining eligibility, and tax outcome deltas
- AI turns computed facts into plain-language guidance
- every answer includes citations when a source-backed tax claim is made
- unsupported year or missing-source queries must refuse cleanly

### C. Tax Chat Copilot

#### v1 outcomes

- start a conversation
- ask Malaysian tax questions by year of assessment
- reference a receipt and get a summary
- ask which category a receipt likely belongs to
- ask for year/category summaries
- ask how to use Clair Tax features
- get structured next-step actions

#### v1 behavior rules

- chatbot is tax-scoped, not a general open-domain assistant
- responses prefer backend and curated tax knowledge over model memory
- receipt-aware answers only use user-scoped receipt data
- mobile uses the same backend contract as web

## Phased Delivery Plan

## Phase 0 - Foundations And Guardrails

### Goals

- establish persistence, contracts, governance, and observability
- avoid building AI features on unstable interfaces

### Tasks

- Add backend Flyway migrations for:
  - `ai_conversations`
  - `ai_messages`
  - `ai_tool_calls`
  - `ai_response_citations`
  - `receipt_category_suggestions`
  - `knowledge_documents`
  - `knowledge_chunks`
  - `ai_model_versions`
  - `ai_prompt_versions`
  - `ai_eval_runs`
  - `ai_eval_results`
- Add backend entities, repositories, DTOs, and services for AI persistence
- Define internal auth for backend <-> AI service calls
- Add audit-friendly request/response correlation IDs
- Add AI governance rules in code:
  - no uncited tax caps
  - no unsupported-year guessing
  - no user-state leakage
  - no auto-filing from AI category suggestions
- Add config scaffolding in backend and AI service for:
  - AI service base URL
  - internal tokens
  - embedding model ID
  - chat model ID
  - retrieval flags
  - streaming flags

### Deliverables

- new schema and persistence layer
- internal integration contract between backend and AI service
- AI config surface in both services

## Phase 1 - Knowledge Base And Retrieval

### Goals

- build the retrieval-first foundation before introducing tax chat

### Tasks

- Define knowledge document schema with:
  - title
  - YA / effective year
  - topic/category code
  - source URL
  - source type
  - publication/effective dates
  - excerpt hash
  - ingestion version
- Build AI-service ingestion pipeline for:
  - curated official LHDN tax relief materials
  - MOF tax-measure documents
  - Clair Tax product/help content
- Normalize documents into chunked records
- Generate embeddings using `multilingual-e5-large-instruct`
- Store vectors in PostgreSQL with `pgvector`
- Build retrieval service with:
  - YA filtering
  - category filtering
  - source filtering
  - top-k retrieval
  - citation mapping
- Add backend admin/internal endpoints or jobs to trigger ingest and re-index

### Deliverables

- versioned curated Malaysian tax corpus
- chunk storage and embeddings
- retrieval API inside AI service

## Phase 2 - Receipt Intelligence Enhancements

### Goals

- improve extraction quality and add category suggestion while preserving human review

### Tasks

- Extend reviewed-receipt export into a versioned dataset pipeline
- Add dataset preparation flow for:
  - reviewed valid receipts
  - rejected receipts
  - mixed-language receipts
  - low-quality image receipts
  - year/category edge cases
- Keep current Textract extraction as the primary OCR path
- Improve trained postprocessor in this order:
  1. amount and date confidence tuning
  2. receipt validity classification
  3. merchant normalization
  4. relief-category suggestion
- Add AI-service output model for receipt insights:
  - summary text
  - suggested category list
  - confidence
  - warnings
- Persist category suggestions and user overrides in backend
- Expose receipt insight endpoint through backend:
  - `POST /api/ai/receipts/{receiptId}/insights`

### Deliverables

- better extraction quality
- merchant normalization
- AI category suggestion with override support
- receipt insight API

## Phase 3 - Backend Tool Surface For Tax Reasoning

### Goals

- expose grounded backend-owned tools to AI service

### Required internal tool endpoints

- `get_policy(year)`
- `get_visible_reliefs(user, year)`
- `get_remaining_relief(user, year, category)`
- `simulate_tax_outcome(user, year, scenario)`
- `list_receipts(user, year, category)`
- `summarize_receipts(user, year, category)`

### Tasks

- Add backend internal controllers, DTOs, and services for each tool
- Reuse existing tax calculator and profile-visibility logic rather than duplicating rules
- Define scenario input shapes for simulation:
  - category ID
  - proposed contribution or spend amount
  - optional receipt scope
- Ensure all tool outputs are deterministic and typed
- Add backend tests for each tool endpoint

### Deliverables

- stable backend-owned tool surface for AI orchestration
- tool contracts ready for LangGraph integration

## Phase 4 - Tax Recommendation Agent

### Goals

- deliver explainable, year-aware, calculator-backed recommendation guidance

### Tasks

- Implement LangGraph workflow in AI service for:
  - question classification
  - retrieval
  - tool selection
  - grounded answer generation
  - citation assembly
  - refusal path
- Add recommendation-specific prompts for:
  - relief optimization
  - missing-cap reminders
  - PRS guidance
  - receipt completion guidance
- Add backend public endpoints:
  - `GET /api/user-years/{year}/ai-summary`
  - optional recommendation feed endpoints if needed
- Add response contract fields:
  - citations
  - confidence
  - structured action cards
  - tool call summaries

### Deliverables

- tax recommendation service backed by retrieval and backend tools
- web-consumable year summary contract

## Phase 5 - Tax Chat Copilot

### Goals

- ship a user-facing conversation interface on web first

### Backend tasks

- Add public conversation endpoints:
  - `POST /api/ai/conversations`
  - `POST /api/ai/conversations/{id}/messages`
  - `GET /api/ai/conversations/{id}/messages`
- Add message persistence and citation persistence
- Add streaming response support if technically feasible within current stack
- Enforce conversation ownership and user scoping

### AI service tasks

- Add chat orchestration workflow for:
  - tax Q&A
  - receipt-referenced questions
  - year/category receipt summaries
  - platform how-to answers
  - next-best-action generation
- Add receipt-aware context loader by receipt ID
- Add response structuring for:
  - text answer
  - citations
  - structured cards
  - follow-up suggestions

### Web tasks

- Build conversation UI in `frontend/`
- Support:
  - thread list or single-thread entry point
  - optimistic message UI
  - streaming answer state
  - citation rendering
  - action-card rendering
  - receipt-linked prompts
- Add calm, tax-workflow-appropriate interface patterns consistent with current shell

### Mobile tasks

- Do not ship first
- Reuse the same backend contract when mobile work begins
- Add conversation screens only after web contract stabilizes

### Deliverables

- web-first tax chat copilot
- backend-owned public conversation contract

## Phase 6 - Fine-Tuning And Evaluation

### Goals

- improve quality only after retrieval + tools baseline exists and is measured

### Tasks

- Build training datasets for:
  - tax QA with expected citations and year
  - platform support conversations
  - backend-calculated recommendation explanations
  - receipt-category classification
- Build frozen evaluation sets for:
  - English tax questions
  - Malay tax questions
  - mixed-language tax questions
  - unsupported-year refusal
  - citation faithfulness
  - remaining-cap correctness
  - receipt-category suggestion accuracy
  - platform workflow correctness
- Run baseline evaluation on untuned `Qwen2.5-7B-Instruct`
- Fine-tune using LoRA/SFT only if the evaluation shows clear value
- Promote fine-tuned models only if they beat baseline on grounded accuracy and hallucination rate

### Deliverables

- repeatable eval pipeline
- promotion criteria for tuned models
- fine-tuned candidate only if justified by metrics

## Phase 7 - Cloud And Production Readiness

### Goals

- separate batch OCR from interactive inference and make both production-ready

### Tasks

- Keep current Lambda worker for:
  - receipt OCR
  - postprocessing
  - batch extraction jobs
- Add new Terraform module for AI inference on AWS ECS with:
  - GPU-backed task definition
  - service autoscaling
  - networking and security groups
  - ECR image integration
  - CloudWatch logs
- Add secrets for:
  - backend internal token
  - model config
  - embedding config
  - optional vendor keys
- Add model artifact and eval artifact storage in S3
- Add monitoring for:
  - response latency
  - retrieval failures
  - tool-call failures
  - citation coverage
  - queue backlog
  - DLQ depth
- Add shadow-mode or internal-only rollout before public launch

### Deliverables

- dual-path AI runtime:
  - Lambda for batch OCR
  - ECS GPU service for interactive inference

## Public API Changes

The backend remains the public contract.

### New public endpoints

- `POST /api/ai/conversations`
- `POST /api/ai/conversations/{id}/messages`
- `GET /api/ai/conversations/{id}/messages`
- `POST /api/ai/receipts/{receiptId}/insights`
- `GET /api/user-years/{year}/ai-summary?reliefCategoryId=...`

### Shared DTO additions

- `AiCitation`
- `AiToolCallSummary`
- `AiActionCard`
- `AiConversationResponse`
- `AiMessageResponse`
- `ReceiptInsightResponse`
- `ReceiptCategorySuggestionResponse`
- `UserYearAiSummaryResponse`

### Required response fields

- answer text
- citations
- confidence
- tool-call summaries
- structured actions
- source year

## Data Model Additions

### Backend tables

- `ai_conversations`
- `ai_messages`
- `ai_tool_calls`
- `ai_response_citations`
- `receipt_category_suggestions`
- `ai_model_versions`
- `ai_prompt_versions`
- `ai_eval_runs`
- `ai_eval_results`

### Knowledge tables

- `knowledge_documents`
- `knowledge_chunks`

### Suggested core columns

#### ai_conversations

- id
- user_id
- title
- status
- created_at
- updated_at

#### ai_messages

- id
- conversation_id
- role
- content
- confidence_score
- model_version
- prompt_version
- created_at

#### ai_tool_calls

- id
- message_id
- tool_name
- request_json
- response_json
- started_at
- completed_at
- status

#### ai_response_citations

- id
- message_id
- knowledge_chunk_id
- source_url
- source_title
- excerpt_hash
- year_of_assessment

#### receipt_category_suggestions

- id
- receipt_id
- suggested_relief_category_id
- rank_position
- confidence_score
- model_version
- created_at
- accepted_at
- rejected_at

#### knowledge_documents

- id
- source_type
- title
- source_url
- year_of_assessment
- topic_code
- effective_from
- effective_to
- document_hash
- ingestion_version
- created_at

#### knowledge_chunks

- id
- document_id
- chunk_index
- chunk_text
- embedding
- excerpt_hash
- created_at

## AI Governance Rules

These rules must be enforced in code, tests, prompts, and rollout reviews.

### Tax accuracy rules

- Tax caps, limits, and eligibility must come from:
  - backend policy/tool results
  - or curated official knowledge documents
- If year or source is missing, the assistant must refuse to answer numerically
- Responses must preserve YA specificity

### User safety rules

- AI cannot auto-confirm receipt reviews
- AI cannot auto-create claims from category suggestions
- AI cannot read receipts outside the current user scope

### Auditability rules

- persist tool calls
- persist citations
- persist model/prompt version used for each response
- persist user corrections to receipt category suggestions

## Resources Required

### Team

- 1 backend engineer
- 1 AI/ML engineer
- 1 frontend engineer
- 1 QA or automation engineer
- 1 Malaysian tax SME or policy owner

### Data

- reviewed receipt exports already available via backend internal API
- official LHDN relief materials by year
- MOF budget and tax-measure documents
- curated Clair Tax product/help content
- reviewed synthetic and human-authored tax conversations

### Infrastructure

- existing PostgreSQL cluster
- `pgvector` extension enabled
- existing S3 and SQS infrastructure
- existing Lambda receipt worker
- new GPU-backed ECS inference service
- ECR for model-serving images
- CloudWatch
- Secrets Manager
- S3 buckets for eval/model artifacts

## Validation Plan

### Receipt extraction

- amount/date extraction accuracy
- validity rejection precision/recall
- merchant normalization quality
- relief-category suggestion top-1 and top-3 accuracy

### Tax recommendation

- every numeric answer must match backend policy/tool results or official source material
- PRS guidance must never exceed remaining eligible cap
- unsupported-year questions must refuse cleanly

### Chat copilot

- receipt upload -> summary -> category suggestion -> confirmation works correctly
- year/category summaries reconcile with stored receipts
- platform how-to answers match actual web behavior
- citations are attached whenever tax claims are made

### Production gates

- no direct client access to model-serving infrastructure
- p95 latency target defined and measured
- shadow-mode or internal rollout before broad release
- evaluation suite passes agreed threshold before production enablement

## Implementation Order

Recommended execution order:

1. Backend AI persistence and internal auth scaffolding
2. Knowledge ingestion and `pgvector` retrieval
3. Backend internal tool surface for tax reasoning
4. Receipt intelligence enhancements and receipt insight endpoint
5. Tax recommendation agent
6. Web-first tax chat copilot
7. Fine-tuning and model promotion workflow
8. Mobile chat adoption
9. Production hardening and rollout expansion

## Initial Milestone Breakdown

### Milestone 1

- add AI schema
- add AI config
- add knowledge ingestion skeleton
- add `pgvector` support

### Milestone 2

- add retrieval service
- add backend internal tool endpoints
- add receipt insight contract

### Milestone 3

- add recommendation agent
- add year-summary endpoint
- add receipt category suggestion persistence

### Milestone 4

- add conversation APIs
- add web chat UI
- add citations and action cards

### Milestone 5

- add eval suite
- add fine-tuning workflow
- add ECS GPU inference deployment

## Risks And Mitigations

### Risk: model hallucination on tax rules

Mitigation:

- retrieval-first design
- backend tool use for numeric answers
- mandatory citations
- refusal path when support is missing

### Risk: duplicate business logic across backend and AI service

Mitigation:

- keep tax rule computation in backend
- expose backend tools instead of reimplementing rules in Python

### Risk: receipt categorization errors impacting claims

Mitigation:

- category suggestions remain advisory
- user confirmation required
- persist accepted vs rejected suggestions for retraining

### Risk: interactive inference cost and latency

Mitigation:

- separate batch OCR from interactive inference
- use a 7B baseline first
- autoscale ECS service
- add retrieval to reduce prompt and model load

## Definition Of Done

The AI initiative is done when:

- receipt intelligence is live with review-safe category suggestions
- tax recommendation answers are grounded, cited, and year-aware
- web tax chat copilot is live on backend-owned contracts
- backend remains the single source of truth for tax rules
- production inference is separated from the Lambda OCR worker
- evaluation gates exist for tax accuracy, citation faithfulness, and refusal behavior
- mobile can adopt the same stable contracts without inventing new logic

## References

- AWS Textract AnalyzeExpense overview:
  - https://docs.aws.amazon.com/textract/latest/dg/analyzing-document-expense.html
- AWS receipt and invoice extraction:
  - https://docs.aws.amazon.com/textract/latest/dg/invoices-receipts.html
- AWS Lambda container images:
  - https://docs.aws.amazon.com/lambda/latest/dg/images-create.html
- AWS Lambda quotas:
  - https://docs.aws.amazon.com/lambda/latest/dg/gettingstarted-limits.html
- AWS ECS GPU support:
  - https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-gpu.html
- LangGraph overview:
  - https://docs.langchain.com/oss/python/langgraph/overview
- vLLM OpenAI-compatible serving:
  - https://docs.vllm.ai/en/latest/serving/openai_compatible_server.html
- pgvector:
  - https://github.com/pgvector/pgvector
- Sentence Transformers multilingual guidance:
  - https://github.com/huggingface/sentence-transformers/blob/main/examples/sentence_transformer/training/multilingual/README.md
- Qwen/Qwen2.5-7B-Instruct:
  - https://huggingface.co/Qwen/Qwen2.5-7B-Instruct
- intfloat/multilingual-e5-large-instruct:
  - https://huggingface.co/intfloat/multilingual-e5-large-instruct
- microsoft/Phi-3.5-vision-instruct:
  - https://huggingface.co/microsoft/Phi-3.5-vision-instruct
