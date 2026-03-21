# 11 — Phase 7: Frontend App

## Objective
Build a clean Next.js application for users and admins.

## Agent Interpretation

- This phase turns backend capabilities into a user-facing and admin-facing interface without moving business logic into the browser.
- Preserve strong separation between public pages, authenticated user experiences, and admin-only surfaces.
- Favor clear loading, error, and empty states early so API and auth edge cases are visible during development instead of being deferred.

## Routes
### Public
- `/`
- `/tax-relief/[year]`
- `/login`
- `/verify`

### User
- `/dashboard`
- `/calculator`
- `/receipts`
- `/receipts/[id]`
- `/suggestions`

### Admin
- `/admin`
- `/admin/policies`
- `/admin/policies/[id]`
- `/admin/reliefs`
- `/admin/brackets`

## Frontend Principles
- TypeScript everywhere
- Central API client
- Zod validation for forms
- Strong loading and error states
- Separate public pages from authenticated layouts

## Codex Task 1
```md
Scaffold Next.js frontend application.

Requirements:
- Use App Router and TypeScript
- Add TailwindCSS
- Create route groups for public, app, and admin pages
- Add shared layout, navbar, and API client structure
- Add placeholder pages for login, dashboard, calculator, receipts, admin
```

## Codex Task 2
```md
Implement magic link login UI.

Requirements:
- Login form to request magic link
- Verify route to exchange token with backend
- Store session securely in HTTP-only cookie flow or secure token strategy consistent with backend
- Redirect authenticated user to dashboard
```

## Codex Task 3
```md
Implement calculator UI.

Requirements:
- Year selector
- Gross income input
- Dynamic relief category form loaded from backend policy API
- Result panel showing:
  - total relief
  - chargeable income
  - bracket breakdown
  - tax payable
- Add save action for authenticated users
```

## Codex Task 4
```md
Implement receipt UI.

Requirements:
- Receipt upload page with year/category selection
- Receipt list page with statuses
- Receipt detail page with extracted amount/date review controls
```

## Review Checklist
- pages are accessible
- forms are validated
- public relief pages are SEO-friendly
- admin UI is auth-protected

## Dependencies and Handoff

- Depends on stable auth, policy, calculator, and receipt APIs or compatible stubs.
- Should unblock admin operations, user self-service tax workflows, and later parity work for mobile without redefining backend contracts.
- Human review should focus on route boundaries, auth enforcement, accessibility, and whether UI semantics match the domain model and API states.
