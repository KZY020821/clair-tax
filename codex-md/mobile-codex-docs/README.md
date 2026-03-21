# Mobile Codex Docs — Tax Relief App MY

This folder contains the Codex work package for building the phone application while preserving a user experience close to the existing Next.js web app.

## Agent Reading Contract

- Read these files in order. Earlier files define product intent and parity rules. Later files should be executed within those constraints.
- Treat this folder as a mobile delivery specification layered on top of the core platform docs, not as permission to redesign the whole product.
- When mobile-specific implementation details are missing, preserve parity with the web product first and introduce native enhancements second.
- If the live repository or backend differs from the examples here, adapt the implementation details while keeping the same user journey, naming, and source-of-truth rules.

## Goal
Build a React Native + Expo mobile app that:
- reuses the Spring Boot backend
- preserves the web information architecture and interaction model
- makes receipt capture and review feel native on phone
- keeps calculations, tax policies, and AI suggestions consistent with the web product

## Reading order
1. `01-mobile-product-scope.md`
2. `02-ux-parity-principles.md`
3. `03-mobile-architecture.md`
4. `04-mobile-repository-structure.md`
5. `05-mobile-backlog.md`
6. `06-mobile-api-contract.md`
7. `07-mobile-screen-spec.md`
8. `08-mobile-state-and-data-flow.md`
9. `09-mobile-auth-and-deep-linking.md`
10. `10-mobile-receipt-upload-flow.md`
11. `11-mobile-notifications-plan.md`
12. `12-mobile-testing-and-release.md`
13. `13-mobile-codex-execution-plan.md`
14. `14-mobile-prompt-library.md`
15. `15-mobile-definition-of-done.md`
16. `16-mobile-AGENTS-template.md`

## How to use with Codex
- Keep tasks small and reviewable.
- One feature branch per task.
- Ask Codex to follow `16-mobile-AGENTS-template.md`.
- Do not ask Codex to redesign the web UX. Ask it to implement parity first, then mobile-specific enhancements second.

## Agent Usage Notes

- Use one feature area per task: auth, navigation, calculator, receipts, suggestions, or release work.
- Pair screen work with `06-mobile-api-contract.md` and `08-mobile-state-and-data-flow.md` so UI, data contracts, and state ownership stay aligned.
- Treat receipt capture, deep-link auth, and notifications as mobile-native surfaces that still inherit backend and product rules from the web platform.
