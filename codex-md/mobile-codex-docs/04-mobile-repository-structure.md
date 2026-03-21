# 04 — Mobile Repository Structure

## Recommended monorepo layout
```text
tax-relief-app/
  frontend-web/
  backend/
  ai-service/
  mobile-app/
    app/
    src/
      components/
      features/
      services/
      hooks/
      store/
      theme/
      utils/
      types/
    assets/
    tests/
    app.config.ts
    package.json
```

## Agent Interpretation

- This structure is meant to keep navigation, reusable UI, feature logic, and integration code discoverable for both humans and agents.
- Prefer adding files to the existing slice-oriented folders over inventing new top-level organizational patterns for each feature.
- If the repo already has a mobile workspace, use this as a normalization target for ownership and discoverability rather than copying the structure mechanically.

## Mobile folder detail

### `app/`
Expo Router entry points and file-based routes.

### `src/components/`
Shared UI pieces:
- cards
- buttons
- inputs
- progress bars
- receipt status chips
- tax summary rows

### `src/features/`
Feature-specific logic:
- auth
- calculator
- receipts
- suggestions
- profile

### `src/services/`
- API client
- auth token refresh
- upload helpers
- analytics
- notification registration

### `src/theme/`
Shared design tokens aligned with web:
- spacing
- typography scale
- colors
- border radius
- shadows

## Codex instruction
Codex should not invent a totally different structure unless there is a strong reason documented in the PR.

## Structure Success Criteria

- a new contributor can find routes, shared components, feature logic, and service integrations quickly
- UI primitives stay reusable instead of being redefined per screen
- API, auth, upload, analytics, and notification concerns are discoverable without mixing them into presentational files
