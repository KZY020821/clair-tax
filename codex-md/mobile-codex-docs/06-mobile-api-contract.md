# 06 — Mobile API Contract

## Rule
The mobile app should consume stable, explicit DTOs. Do not expose internal persistence models.

## Agent Interpretation

- This file defines the mobile-facing contract layer. It is the preferred shape of data exchanged with the app, even if backend internals use different entities or tables.
- Keep DTO names and response semantics stable so mobile UI, query hooks, and tests do not depend on persistence implementation details.
- If the backend already exposes a slightly different contract, adapt carefully and update both the docs and generated client types together.

## Auth endpoints
### `POST /api/mobile/auth/request-link`
Request body:
```json
{ "email": "user@example.com" }
```

### `POST /api/mobile/auth/verify`
Request body:
```json
{ "token": "opaque-or-jwt-token", "deviceName": "iPhone 15 Pro" }
```
Response:
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "user": {
    "id": "uuid",
    "email": "user@example.com"
  }
}
```

### `POST /api/mobile/auth/refresh`
### `POST /api/mobile/auth/logout`

## Policy endpoints
### `GET /api/mobile/policies/{year}`
Response includes:
- year
- relief categories
- bracket table
- calculator UI metadata

## Calculation endpoints
### `POST /api/mobile/calculations`
Request:
```json
{
  "year": 2025,
  "grossIncome": 120000,
  "claims": [
    { "reliefCategoryId": "uuid-1", "amount": 3000 }
  ]
}
```

Response:
```json
{
  "grossIncome": 120000,
  "totalRelief": 3000,
  "taxableIncome": 117000,
  "taxAmount": 10123.45,
  "breakdown": []
}
```

## Receipt endpoints
### `POST /api/mobile/receipts/upload-url`
Returns:
- upload URL
- object key
- receipt draft id

### `POST /api/mobile/receipts/complete`
Mark upload complete and enqueue OCR.

### `GET /api/mobile/receipts`
### `GET /api/mobile/receipts/{id}`
### `PATCH /api/mobile/receipts/{id}`

## Suggestion endpoints
### `GET /api/mobile/suggestions?year=2025`

## Device endpoints
### `POST /api/mobile/devices/register-push-token`

## Contract rule
Codex must update API docs and TypeScript types together.

## Contract Stability Notes

- Mobile endpoints should optimize for explicitness and backward-compatible evolution because app releases may lag backend deploys.
- Auth, calculation, receipt, and notification flows should return enough state for the app to render clear UI without inferring hidden server behavior.
