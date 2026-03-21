# 14 — Mobile Prompt Library

## How to Use These Prompts

- Use one prompt at a time and add current repository details such as exact folders, package names, validation commands, and screens already implemented.
- Treat each prompt as a starting point for a small, reviewable task, not as a request to finish an entire mobile feature area at once.
- Pair prompts with the parity, API contract, and state-flow docs when the task affects user-facing behavior or data ownership.

## Recommended Prompt Suffix

Append a short repo-specific suffix like this when using a prompt:

```md
Repo notes:
- target path(s): ...
- related screen(s) or route(s): ...
- validation commands: ...
- do not modify: ...
```

## Prompt 1 — Scaffold app
Create a new Expo TypeScript mobile app in `mobile-app/` with Expo Router, ESLint, Prettier, and strict TypeScript. Do not modify the web or backend folders.

## Prompt 2 — Build theme parity
Create a shared theme system for the mobile app that mirrors the visual hierarchy of the Next.js app: spacing scale, typography scale, card radius, shadows, and semantic colors. Do not redesign the product.

## Prompt 3 — Login screen
Implement a mobile login screen with email input, send-link action, loading state, success state, and error state. Use the existing backend endpoint contract in `06-mobile-api-contract.md`.

## Prompt 4 — Deep link verification
Implement deep link parsing and token verification flow. When verification succeeds, persist the session securely and route to the dashboard.

## Prompt 5 — Dashboard
Build a dashboard screen with cards for Calculator, Relief Tracker summary, Receipts, Suggestions, and Profile. Match naming and hierarchy with the web app.

## Prompt 6 — Calculator shell
Build the calculator screen with year selector, income field, relief groups, submit button, and result summary placeholders. No hardcoded tax rules.

## Prompt 7 — Calculator integration
Integrate the calculator screen with the mobile policy and calculation endpoints. Render relief categories dynamically from the backend response.

## Prompt 8 — Receipt upload
Implement a scan/upload flow using camera or picker, request a pre-signed upload URL, upload directly to S3, then mark the upload complete through the backend.

## Prompt 9 — Receipt review
Implement the receipt review screen with image preview, amount field, date field, category selector, confirm button, and validation errors.

## Prompt 10 — Suggestions
Implement a suggestions screen that renders AI suggestion cards with potential savings and a CTA to navigate to the related calculator section.

## Prompt 11 — Push notifications
Add push token registration and notification tap handling. Route users to the correct screen based on notification payload.

## Prompt 12 — Test pass
Add unit and integration tests for auth, calculator submission, and receipt upload helpers. Keep coverage focused on critical paths.
