# 09 — Mobile Auth and Deep Linking

## Desired flow
1. user types email
2. backend sends magic link
3. user taps email link
4. app opens via deep link
5. app extracts token
6. app calls verify endpoint
7. tokens stored securely
8. user lands on dashboard

## Agent Interpretation

- This flow is the mobile realization of the same magic-link auth model used on web, adapted for deep links and device session storage.
- Keep deep-link parsing, token verification, secure persistence, and logout behavior modular so auth bugs are easier to isolate and test.
- Treat expired links, already-used links, and resend behavior as first-class user journeys, not edge polish.

## Requirements
- use universal links / app links where possible
- short-lived one-time token
- handle expired link gracefully
- handle already-used token gracefully
- allow resend link flow

## Storage
- access token: secure storage
- refresh token: secure storage
- session metadata: secure storage

## Security expectations
- clear session on logout
- support remote revoke
- rate limit request-link endpoint
- audit login events on backend

## Codex task split
- task 1: login screen UI
- task 2: deep link config
- task 3: verify token flow
- task 4: secure session persistence
- task 5: logout and token refresh

## Integration Boundary

- The app should never decide whether a token is valid based on local logic alone; backend verification remains authoritative.
- Secure storage should contain only the minimum session material needed for authenticated app use and token refresh.
- Human review should focus on deep-link reliability, token handling, logout cleanup, and recoverability when auth fails mid-flow.
