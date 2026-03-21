# 12 — Mobile Testing and Release

## Test layers

## Agent Interpretation

- Mobile testing should protect parity with web semantics and resilience across device-specific flows like deep links, uploads, and notification routing.
- Focus first on the flows most likely to break because of platform differences or async behavior.
- Use test fixtures and manual QA cases that compare mobile results against backend and web expectations where possible.

### Unit tests
- formatting utilities
- DTO mappers
- upload state reducers
- screen-level simple logic

### Integration tests
- auth flow
- calculator submission
- receipt upload service
- suggestion fetch flow

### E2E tests
- login
- calculate tax
- upload receipt
- review OCR result
- logout

## Device testing matrix
- iPhone recent model
- iPhone older supported model
- Android recent model
- Android lower-end mid-range device

## Release gates
- no critical crashes
- auth happy path works
- calculator totals match web for test fixtures
- receipt upload succeeds on Wi-Fi and mobile network
- deep links verified
- monitoring enabled

## Delivery
- internal QA build
- TestFlight / Android internal testing
- staged production rollout

## Release Readiness Reminder

- A mobile build is not ready just because it compiles; device behavior, deep links, secure session handling, and network variability must also be verified.
- Human review should focus on parity with web outputs, platform-specific regressions, and crash visibility before expanding rollout.
