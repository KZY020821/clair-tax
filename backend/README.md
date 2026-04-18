# Backend

Minimal Spring Boot scaffold for the Clair Tax API.

## Stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL driver
- Flyway

## Local setup

1. Start the app with the local scaffold profile:
   `./mvnw spring-boot:run`
2. Check the health endpoints:
   - `http://localhost:8080/api/health`
   - `http://localhost:8080/actuator/health`

## Notes

- The default `local` profile disables database, JPA, and Flyway auto-configuration so the scaffold can start independently before infrastructure is added.
- Database placeholders are already present in `src/main/resources/application.yml` for local PostgreSQL wiring.
- Flyway migrations live in `src/main/resources/db/migration`.

## Apply the PostgreSQL schema

1. Create a local PostgreSQL database, for example `clair_tax`.
2. Export connection settings if you are not using the defaults:
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/clair_tax`
   - `SPRING_DATASOURCE_USERNAME=postgres`
   - `SPRING_DATASOURCE_PASSWORD=postgres`
   - Optional for phone-friendly auth links during local mobile testing:
     - `CLAIR_AUTH_PUBLIC_BASE_URL=http://<your-laptop-ip>:8080`
     - `CLAIR_AUTH_MOBILE_APP_SCHEME=clair-tax`
   - Optional for real magic-link email delivery:
     - `SPRING_MAIL_USERNAME=your-smtp-username`
     - `SPRING_MAIL_PASSWORD=your-smtp-password-or-app-password`
3. Run the backend with a non-`local` profile so Flyway executes on startup:
   `SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run`

The initial migration creates the platform tables, keys, and indexes in PostgreSQL.
The second migration seeds demo rows, including `policy_year` records that back the sample frontend integration view.

## Demo endpoint

- `POST /api/auth/magic-link/request`
- `POST /api/auth/otp/request`
- `POST /api/auth/otp/verify`
- `GET /api/auth/session`
- `GET /api/auth/magic-link/verify?token=...`
- `GET /api/auth/mobile-link?token=...`
- `POST /api/auth/logout`
- `GET /api/policy-years`
- `GET /api/policies/{year}`
- `POST /api/calculator/calculate`
- `GET /api/dev/me`
- `GET /api/profile`
- `PUT /api/profile`
- `DELETE /api/profile/account`
- `GET /api/user-years`
- `POST /api/user-years`
- `GET /api/user-years/{year}`
- `GET /api/user-years/{year}/receipts`
- `POST /api/user-years/{year}/receipts`
- `GET /api/receipts/years`
- `GET /api/receipts?year=2025`
- `GET /api/receipts/{id}`
- `GET /api/receipts/{id}/file`
- `POST /api/receipts`
- `PUT /api/receipts/{id}`
- `DELETE /api/receipts/{id}`

These endpoints are available when the backend is started with a database-backed profile such as `postgres`.

## Local auth modes

The backend now supports two localhost-oriented auth paths:

- Web localhost auth can use magic-link sessions through `/api/auth/magic-link/request`, `/api/auth/session`, `/api/auth/magic-link/verify`, and `/api/auth/logout`.
- Mobile localhost auth should prefer the email OTP flow through `/api/auth/otp/request` and `/api/auth/otp/verify`.
- Mobile magic-link emails now open a non-consuming `/api/auth/mobile-link` bridge page that hands off to `clair-tax://auth/verify?...` before the app calls `/api/auth/magic-link/verify`.
- `/api/dev/me` still exists for local mobile fallback flows, debug visibility, and destructive-reset scenarios where the temporary local account is still in use.
- If SMTP credentials are not configured on localhost or a private-network dev host, the magic-link request returns a local debug verify URL instead of failing with a `500`, and OTP requests can return a debug code for in-app testing.

## Temporary local account fallback

- Default dev email: `dev@taxrelief.local`
- Config override: `CLAIR_DEV_USER_EMAIL`
- Bootstrap behavior: on database-backed startup, the backend automatically creates the dev user if it does not already exist
- Resolution behavior: if no authenticated browser session is present, current-user endpoints fall back to the configured local account and never accept `userId` from the client
- Saved profile behavior: the temporary local account also stores disability, marital status, spouse, and children fields used by the calculator and year workspace
- Local receipt storage path: `CLAIR_RECEIPTS_STORAGE_PATH` (defaults to `/tmp/clair-tax-receipts`)

Debug and fallback endpoint:

- `GET /api/dev/me`

Example response:

```json
{
  "id": "c9c2d34c-2086-44e8-8f1e-7cf2368d8c2f",
  "email": "dev@taxrelief.local",
  "mode": "dev"
}
```

## Tax calculator endpoint

`POST /api/calculator/calculate`

The calculator reads relief caps and progressive tax brackets from the selected policy year's database records.
The current schema uses UUID primary keys for relief categories, so `reliefCategoryId` is a UUID string in the API.
Saved-profile household fields are resolved server-side from `GET /api/profile`, so spouse and disability reliefs are no longer trusted from frontend-only state.

Example request:

```json
{
  "policyYear": 2025,
  "grossIncome": 85000.00,
  "selectedReliefs": [
    {
      "reliefCategoryId": "44444444-4444-4444-8444-444444444441",
      "claimedAmount": 4000.00
    },
    {
      "reliefCategoryId": "44444444-4444-4444-8444-444444444442",
      "claimedAmount": 1800.00
    },
    {
      "reliefCategoryId": "44444444-4444-4444-8444-444444444443",
      "claimedAmount": 1200.00
    }
  ]
}
```

Example response:

```json
{
  "policyYear": 2025,
  "grossIncome": 85000.00,
  "totalRelief": 7000.00,
  "chargeableIncome": 78000.00,
  "taxBreakdown": [
    {
      "minIncome": 0.00,
      "maxIncome": 50000.00,
      "rate": 1.00,
      "taxableAmount": 50000.00,
      "taxForBracket": 500.00
    },
    {
      "minIncome": 50000.01,
      "maxIncome": 100000.00,
      "rate": 3.00,
      "taxableAmount": 28000.00,
      "taxForBracket": 840.00
    },
    {
      "minIncome": 100000.01,
      "maxIncome": null,
      "rate": 5.00,
      "taxableAmount": 0.00,
      "taxForBracket": 0.00
    }
  ],
  "totalTaxPayable": 1340.00
}
```

Validation behaviour:

- `grossIncome` and each `claimedAmount` must be zero or greater.
- Each requested relief claim is capped at that category's `maxAmount`.
- Relief categories must belong to the selected policy year.
- Relief categories that do not match the saved current-user profile are rejected.
- Saved-profile fixed reliefs such as disabled individual and spouse reliefs are applied automatically by the backend.
- Missing policy year values return a validation error and unknown policy years return `404`.

## Profile endpoints

The current active user has a saved household profile used by the calculator and year workspace. On localhost web this can be the signed-in magic-link session, while mobile and other local fallback flows can still resolve to the temporary local account.

### `GET /api/profile`

Example response:

```json
{
  "id": "c9c2d34c-2086-44e8-8f1e-7cf2368d8c2f",
  "email": "dev@taxrelief.local",
  "isDisabled": false,
  "maritalStatus": "single",
  "spouseDisabled": null,
  "spouseWorking": null,
  "hasChildren": null
}
```

### `PUT /api/profile`

Example request:

```json
{
  "isDisabled": true,
  "maritalStatus": "married",
  "spouseDisabled": false,
  "spouseWorking": false,
  "hasChildren": true
}
```

### `DELETE /api/profile/account`

This removes the current active user's saved year workspaces, receipts, receipt files, and saved profile fields. In local fallback flows it effectively resets the temporary local account back to the default profile.

## Policy endpoint

`GET /api/policies/{year}`

This endpoint returns a single policy year and its relief categories so the web
and mobile clients can render year-specific claim inputs from backend-managed
data.

Example response:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "year": 2025,
  "status": "published",
  "reliefCategories": [
    {
      "id": "44444444-4444-4444-8444-444444444442",
      "name": "Lifestyle",
      "description": "Books, devices, sports equipment, and internet subscriptions.",
      "maxAmount": 2500.00,
      "requiresReceipt": true
    },
    {
      "id": "44444444-4444-4444-8444-444444444441",
      "name": "Self and Dependent",
      "description": "Baseline relief for the taxpayer and dependents.",
      "maxAmount": 9000.00,
      "requiresReceipt": false
    }
  ]
}
```

## Useful commands

- `./mvnw spring-boot:run`
- `SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run`
- `./mvnw test`

## Year workspace flow

`user_policy_years` is the canonical parent for a user's year-specific receipt workspace.
The frontend flow is:

1. `POST /api/user-years` to create a year workspace from an existing `policy_year`
2. `GET /api/user-years/{year}` to load the year summary and category caps
3. `POST /api/user-years/{year}/receipts` to upload a receipt file and manual amount into that year
4. `GET /api/user-years/{year}/receipts` to review the receipts already stored for that year

`user_relief_claims` now hangs off `user_policy_years`, and receipt create/update/delete operations keep those claim totals in sync.

## Year workspace endpoints

All year-workspace endpoints are scoped to the current active user.

### `GET /api/user-years`

Returns the year workspaces already created by the current active user, sorted descending.

Example response:

```json
[
  {
    "id": "1b2a3628-86de-442c-8c60-389e915ae230",
    "year": 2025,
    "status": "published",
    "createdAt": "2026-03-23T06:30:00Z",
    "updatedAt": "2026-03-23T06:30:00Z"
  }
]
```

### `POST /api/user-years`

Creates the requested year workspace for the current active user. If the workspace already exists, the existing row is returned.

Example request:

```json
{
  "policyYear": 2025
}
```

### `GET /api/user-years/{year}`

Returns the current active user's year summary, including only the relief categories relevant to the saved profile for that policy year, plus each category's cap, claimed amount, remaining amount, and receipt count.

### `GET /api/user-years/{year}/receipts`

Returns the current active user's receipts for the requested year workspace.

### `POST /api/user-years/{year}/receipts`

Uploads a receipt into the requested year workspace using `multipart/form-data`.

Required form fields:

- `merchantName`
- `receiptDate`
- `amount`
- `reliefCategoryId`
- `file`

Optional form field:

- `notes`

Example `curl` request:

```bash
curl -X POST http://localhost:8080/api/user-years/2025/receipts \
  -F merchantName="Clinic" \
  -F receiptDate=2025-06-12 \
  -F amount=120.50 \
  -F reliefCategoryId=44444444-4444-4444-8444-444444444541 \
  -F notes="Follow-up visit" \
  -F file=@/path/to/receipt.pdf
```

Uploaded files are stored locally in dev mode and returned with a `fileUrl` such as `/api/receipts/{id}/file`.

## Receipt endpoints

All receipt endpoints are scoped to the current active user.

### `GET /api/receipts/years`

Returns the distinct assessment years where the current active user already has at least one receipt.
The list is sorted descending.

Example response:

```json
[2025, 2024]
```

### `GET /api/receipts?year=2025`

Returns the current active user's receipts for the requested year.

If `year` is omitted, the API returns all receipts for the current active user across all years, ordered by year descending and then receipt date descending.

Example response:

```json
[
  {
    "id": "3bb7af43-7648-4cc4-8ed9-5f3d6d1bf4ad",
    "policyYear": 2025,
    "merchantName": "Popular Bookstore",
    "receiptDate": "2025-05-12",
    "amount": 89.90,
    "reliefCategoryId": "44444444-4444-4444-8444-444444444541",
    "reliefCategoryName": "Lifestyle",
    "notes": "Books and stationery for claim review.",
    "fileName": "popular-2025-05-12.pdf",
    "fileUrl": "https://example.com/receipts/popular-2025-05-12.pdf",
    "createdAt": "2026-03-23T06:30:00Z",
    "updatedAt": "2026-03-23T06:30:00Z"
  }
]
```

### `GET /api/receipts/{id}`

Returns one receipt only if it belongs to the current active user. Other users' receipts return `404`.

### `GET /api/receipts/{id}/file`

Returns the stored local file for a receipt only if that receipt belongs to the current active user and was uploaded through the year workspace flow.

### `POST /api/receipts`

Creates a receipt for the current active user.
This JSON endpoint remains useful for direct CRUD and testing. The primary UI flow now goes through `/api/user-years` and year-scoped upload.

Example request:

```json
{
  "policyYear": 2025,
  "merchantName": "Popular Bookstore",
  "receiptDate": "2025-05-12",
  "amount": 89.90,
  "reliefCategoryId": "44444444-4444-4444-8444-444444444541",
  "notes": "Books and stationery for claim review.",
  "fileName": "popular-2025-05-12.pdf",
  "fileUrl": "https://example.com/receipts/popular-2025-05-12.pdf"
}
```

### `PUT /api/receipts/{id}`

Updates a receipt only if it belongs to the current active user.

### `DELETE /api/receipts/{id}`

Deletes a receipt only if it belongs to the current active user.

### Receipt validation

- `amount` must be zero or greater
- `policyYear` must exist in `policy_year`
- `reliefCategoryId` is optional
- if `reliefCategoryId` is provided, it must exist and belong to the same `policyYear`
- year-scoped uploads require a receipt file and a `reliefCategoryId`
- receipts are always filtered by the current active user, whether that resolves to a browser session or the temporary local fallback account
