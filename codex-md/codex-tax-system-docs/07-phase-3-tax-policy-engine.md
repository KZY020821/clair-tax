# 07 — Phase 3: Tax Policy Engine

## Objective
Create the versioned tax-rule foundation that powers yearly pages, the calculator, and admin updates.

## Agent Interpretation

- This phase defines the data model and publishing workflow that all later tax behavior depends on.
- The main goal is to encode policy as auditable data, not to build every consumer of that data yet.
- Prefer explicit versioning, validation, and publish-state transitions over convenience shortcuts that make yearly policy edits harder to reason about.

## Core Idea
Treat tax policy as data.

## Core Tables
- policy_year
- tax_bracket
- relief_category
- relief_rule
- policy_publish_log

## Suggested Data Model
### policy_year
- id
- year
- status (`draft`, `published`, `archived`)
- effective_from
- effective_to
- published_at
- created_at
- updated_at

### tax_bracket
- id
- policy_year_id
- min_income
- max_income
- tax_rate
- base_tax
- sort_order

### relief_category
- id
- policy_year_id
- code
- name
- description
- max_amount
- requires_receipt
- group_name
- is_active

### relief_rule
- id
- relief_category_id
- rule_type
- config_json

## Admin Workflow
1. Clone prior year policy.
2. Edit draft policy.
3. Review changes.
4. Publish policy.
5. Lock published policy from casual edits.

## Codex Task 1
```md
Implement policy year and tax bracket domain.

Requirements:
- Create JPA entities and Flyway migrations for policy_year and tax_bracket
- Add admin CRUD APIs for draft policy years and brackets
- Add service layer and DTOs
- Add validation to prevent overlapping bracket ranges per year

Constraints:
- Published policy years cannot be edited directly
- Keep controllers thin
- Add service unit tests
```

## Codex Task 2
```md
Implement relief category and relief rule domain.

Requirements:
- Create entities, migrations, repositories, services, controllers
- Support rule config as JSON field
- Add admin API to clone all relief categories from one policy year to another
- Add integration tests for clone flow
```

## Codex Task 3
```md
Implement policy publishing workflow.

Requirements:
- Add endpoint to publish a draft policy year
- Validate required data exists before publish
- Write policy_publish_log entry
- Prevent modifications after publish except through explicit admin override mechanism placeholder

Validation:
- integration tests for publish success and publish failure
```

## Review Checklist
- policy data is not hardcoded
- clone flow is deterministic
- publish action is auditable
- validation prevents broken bracket schedules

## Dependencies and Handoff

- Depends on the backend foundation and authentication or admin guard strategy being available.
- Should unblock the calculator, public yearly content, admin review flows, and future mobile clients through stable published-policy reads.
- When in doubt, optimize for deterministic reads of published policy and safe mutation of draft policy.
