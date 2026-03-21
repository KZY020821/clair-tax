# 05 — Mobile Backlog

## Agent Backlog Interpretation

- Treat each bullet as a reviewable task seed, not as a mandate to implement a whole epic in one change.
- Keep cross-cutting setup work small enough that later feature tasks can build on it without massive rebases.
- Preserve the epic order unless the real repository has already completed or superseded earlier work.

## Epic 1 — Mobile foundation
- Initialize Expo TypeScript app
- Configure Expo Router
- Add ESLint, Prettier, TypeScript strict mode
- Set up environment config
- Set up API client
- Add Sentry
- Add app theme matching web

## Epic 2 — Auth
- Build email entry screen
- Build verify-link handler screen
- Implement deep link parsing
- Implement access token + refresh token flow
- Persist session securely
- Add logout

## Epic 3 — Navigation and dashboard
- Create bottom tab navigation
- Build dashboard cards
- Match module names with web
- Add year picker in shared context

## Epic 4 — Calculator
- Build year selector
- Build income input
- Build relief input list
- Build results summary
- Add save calculation
- Add error state handling

## Epic 5 — Receipts
- Camera capture
- Gallery/file picker
- Image compression
- Pre-signed upload request
- Direct upload to S3
- Receipt list screen
- Receipt detail / review screen

## Epic 6 — Suggestions
- Suggestions list screen
- Potential savings card
- Explanation details
- CTA to relevant category

## Epic 7 — Profile and settings
- Profile summary
- Notification preferences
- Privacy / retention info
- Sign out

## Epic 8 — Release
- QA pass
- TestFlight build
- Android internal testing
- Monitoring dashboards

## Usage Note

- Pair backlog items with the screen spec, API contract, and state-flow docs before implementation so UX and data decisions stay aligned.
- When one backlog item affects auth, upload, or notifications, document the exact boundary of the change because those features have device-specific edge cases.
