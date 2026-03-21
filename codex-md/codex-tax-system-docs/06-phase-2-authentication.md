# 06 — Phase 2: Authentication

## Objective
Implement magic link login with strong session handling.

## Agent Interpretation

- This phase is about secure login flow and session issuance, not full identity management or role administration.
- Keep the authentication contract generic enough that both web and future mobile clients can reuse it.
- Use clear interfaces for email delivery, token generation, and session persistence so production integrations can be swapped in without rewriting core logic.

## Functional Flow
1. User enters email.
2. System creates one-time token.
3. Email is sent with magic link.
4. User clicks link.
5. Backend validates token.
6. Session/JWT is issued.
7. Frontend stores session securely.

## Required Backend Entities
- users
- auth_magic_links
- user_sessions or refresh_tokens

## Suggested Columns
### users
- id
- email
- status
- created_at
- last_login_at

### auth_magic_links
- id
- user_id
- email
- token_hash
- expires_at
- used_at
- requested_ip
- requested_user_agent
- created_at

## Security Requirements
- token stored hashed, not plaintext
- short expiry
- one-time use
- rate limit request endpoint
- prevent account enumeration
- audit login completion

## Codex Task 1
```md
Implement magic link request flow.

Requirements:
- POST `/api/auth/request-link`
- Accept email
- Create user if not exists
- Create one-time token record with hashed token
- Stub email sender service interface
- Return generic success message

Constraints:
- Do not reveal whether email exists
- Add validation and rate-limiting hook points
- Add Flyway migration
- Add unit tests for token creation and expiry handling
```

## Codex Task 2
```md
Implement magic link verification flow.

Requirements:
- POST `/api/auth/verify`
- Accept token and email
- Validate active token
- Mark token as used
- Issue access token and refresh token
- Return session response DTO

Constraints:
- Reject expired or replayed token
- Audit successful login
- Add integration tests for happy path and expired token path
```

## Frontend Work
- login request page
- “check your email” confirmation page
- verify route handler page
- authenticated layout guard

## Review Checklist
- generic response prevents enumeration
- token replay blocked
- tests cover expiration and reuse
- no plaintext token persistence

## Dependencies and Handoff

- Depends on backend foundation, migrations, and shared error handling from the prior phase.
- Should unblock guarded frontend routes, session-aware APIs, and later mobile deep-link verification without changing auth semantics.
- Human review should focus on token lifecycle, replay protection, enumeration resistance, and consistency between backend and frontend session strategy.
