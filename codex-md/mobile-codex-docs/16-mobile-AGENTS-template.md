# AGENTS.md — Mobile App Instructions

## How to Adapt This Template

- Replace generic commands, folder names, and package choices with the actual mobile workspace values before using this as a live `AGENTS.md`.
- Keep the parity, security, and backend-source-of-truth rules even if the real project uses equivalent libraries or wrappers.
- Use this template together with the rest of the mobile docs so repo-specific details stay anchored to the intended product behavior.

## Project summary
This repository contains a Malaysian personal tax management system.
The mobile app must preserve a user experience similar to the Next.js web application while reusing the Spring Boot backend.

## Core rules
1. Do not hardcode annual tax policy logic in the mobile app.
2. Use backend-driven policy data and DTOs.
3. Preserve module naming and user flow parity with the web app.
4. Do not redesign the information architecture without explicit instruction.
5. Keep PRs small and focused.
6. Update tests and docs when behavior changes.

## Mobile stack
- React Native
- Expo
- TypeScript
- Expo Router
- TanStack Query
- Expo SecureStore

## Backend assumptions
- Spring Boot backend remains source of truth
- PostgreSQL for core data
- S3 for receipt storage
- SQS for OCR pipeline

## UX parity requirements
- same dashboard modules as web
- same calculator steps as web
- same receipt status language as web
- same AI suggestion semantics as web

## Security rules
- store tokens securely
- clear tokens on logout
- never log sensitive tokens
- use pre-signed upload URLs for receipt uploads

## Expected commands
- install: `npm install`
- dev: `npm run dev`
- test: `npm test`
- lint: `npm run lint`

## Deliverable expectations
When asked to build a feature:
- modify only the relevant files
- explain assumptions in the PR description
- add or update tests
- include a short manual QA checklist

## Template Boundary

- This file is a starter instruction set, not a replacement for repository discovery.
- If the actual mobile repo has stricter security, release, or testing conventions, those stricter rules should be copied into the final `AGENTS.md`.
