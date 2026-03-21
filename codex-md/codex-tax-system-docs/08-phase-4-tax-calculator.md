# 08 — Phase 4: Tax Calculator

## Objective
Implement deterministic yearly tax calculation based on policy tables and user-entered relief values.

## Agent Interpretation

- The calculator is a pure domain-heavy feature and should stay deterministic, explainable, and testable.
- Separate transient estimation from saved user data so the same calculation logic can power previews, persisted runs, and future suggestion features.
- Do not let frontend formatting concerns or storage shortcuts leak into the core calculation service design.

## Inputs
- selected policy year
- gross income
- per-category claimed amounts
- optional residency flags in future extension

## Outputs
- total relief
- chargeable income
- tax by bracket
- total tax payable
- category cap usage summary

## Core Design
The calculator service should:
1. Load published policy by year.
2. Normalize user claims against category caps/rules.
3. Sum valid relief.
4. Compute chargeable income.
5. Apply progressive tax brackets.
6. Return full breakdown.

## Domain Tables
- user_tax_profile
- user_relief_claim
- calculator_run_log (optional)

## Codex Task 1
```md
Implement calculator domain and service.

Requirements:
- Create user_tax_profile and user_relief_claim tables
- Implement calculator service that loads published policy and computes:
  - capped relief per category
  - total relief
  - chargeable income
  - progressive tax by brackets
  - final tax amount
- Add response DTO with full breakdown

Constraints:
- use BigDecimal for money calculations
- no floating point arithmetic
- add table-driven unit tests for multiple income scenarios
```

## Codex Task 2
```md
Implement calculator API.

Requirements:
- POST `/api/calculator/estimate`
- Request includes policyYear, grossIncome, category claims
- Response returns full calculation summary
- Add validation errors for invalid categories or negative claims

Constraints:
- calculator should not persist data unless explicit save endpoint is called
```

## Codex Task 3
```md
Implement save calculation endpoint.

Requirements:
- POST `/api/calculator/save`
- Persist user tax profile and relief claim records
- Return saved profile id
- Require authenticated user
```

## Review Checklist
- money uses BigDecimal
- bracket math is deterministic
- caps are enforced per year
- tests cover edge thresholds and zero-tax cases

## Dependencies and Handoff

- Depends on published policy data and relief metadata from the policy engine.
- Should unblock calculator APIs, frontend calculator UIs, saved profiles, and future AI suggestions that rely on consistent baseline calculations.
- Human review should focus on edge thresholds, rounding behavior, cap enforcement, and the readability of the returned breakdown.
