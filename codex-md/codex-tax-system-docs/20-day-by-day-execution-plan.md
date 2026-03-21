# 20 — Day-by-Day Execution Plan

## Agent Scheduling Note

- The “days” here represent a dependency-friendly sequence, not a mandatory calendar duration.
- Teams can compress or extend the timeline as long as prerequisite work is still completed before dependent work begins.
- Do not use this plan as justification to combine several days into one oversized Codex task if reviewability would suffer.

## Day 1
- create repo
- create root structure
- add AGENTS.md
- commit initial skeleton
- ask Codex for backend foundation

## Day 2
- review backend foundation
- ask Codex for auth request-link flow
- ask Codex for auth verify flow

## Day 3
- ask Codex for policy year + bracket domain
- review migrations carefully

## Day 4
- ask Codex for relief category + rule domain
- ask Codex for clone workflow

## Day 5
- ask Codex for publish workflow
- add admin test coverage

## Day 6
- ask Codex for calculator domain and service
- manually verify bracket math

## Day 7
- ask Codex for calculator API
- ask Codex for save calculation flow

## Day 8
- ask Codex for receipt metadata domain
- ask Codex for upload intent API

## Day 9
- ask Codex for S3 + queue abstraction
- ask Codex for receipt review flow

## Day 10
- scaffold FastAPI service with Codex
- add extraction models and tests

## Day 11
- scaffold Next.js frontend
- add login and dashboard pages

## Day 12
- add calculator UI
- add receipt pages

## Day 13
- add admin pages
- add publish flow UI

## Day 14
- add tests, CI, and staging deploy
- run launch checklist

## Important note
Do not parallelize tightly coupled schema tasks unless you are confident in merge order.

## Practical Use

- Use this file to decide what should come next after a review, not to remove review checkpoints between phases.
- If the repository already contains some completed phases, resume from the earliest unfinished dependency instead of restarting the entire plan.
