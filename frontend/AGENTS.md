# AGENTS.md

## Frontend scope
This folder contains the Next.js frontend for Clair Tax.

## Frontend rules
- Follow existing Next.js App Router patterns unless the repository clearly uses something else.
- Prefer Server Components by default.
- Add "use client" only when interactivity, browser APIs, or client hooks are required.
- Keep components small, composable, and easy to scan.
- Avoid pushing domain logic into UI components.

## UX standards
- Build polished, practical interfaces.
- Always account for loading, empty, error, and success states where relevant.
- Keep forms understandable and responsive.
- Use plain language.
- Make primary actions obvious.

## Styling
- Prefer existing design patterns and shadcn/ui components.
- Reuse spacing, typography, and container patterns already present in the app.
- Avoid overly dense layouts.

## API usage
- Keep fetch logic predictable and typed.
- Do not hardcode backend URLs in components.
- Handle network failures clearly.

## Boundaries
- The frontend should present and collect data, not become the source of truth for tax rules.
