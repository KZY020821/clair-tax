---
name: tax-domain
description: Use this skill when implementing or editing Malaysian tax concepts in Clair Tax, including Year of Assessment logic, relief calculations, rebates, eligibility rules, and tax-sensitive user flows.
---

# Tax Domain Skill

## Purpose
Use this skill for tax-related logic and tax-facing UX.

## Core rules
- Treat tax logic as correctness-sensitive.
- Make Year of Assessment handling explicit.
- Do not bury tax assumptions in UI code.
- Keep relief eligibility rules traceable and easy to test.
- Prefer pure functions for calculations where practical.

## Modeling guidance
- Separate raw user input from derived tax values.
- Keep money handling explicit and consistent.
- Be careful with annual limits, category caps, and conditional eligibility.
- Use clear names for computed fields.

## UX guidance
- Present tax outcomes in plain language.
- Show assumptions and input dependencies clearly.
- Avoid ambiguous labels around relief, rebate, deduction, and chargeable income.

## Validation
- Validate numeric ranges and required fields.
- Handle partial input gracefully when the user is still filling a form.
- Prefer backend validation for authoritative checks.

## Output expectations
- Generated code should make tax rules understandable, testable, and reviewable.
