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
3. Run the backend with a non-`local` profile so Flyway executes on startup:
   `SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run`

The initial migration creates the platform tables, keys, and indexes in PostgreSQL.
The second migration seeds demo rows, including `policy_year` records that back the sample frontend integration view.

## Demo endpoint

- `GET /api/policy-years`
- `GET /api/policies/{year}`
- `POST /api/calculator/calculate`

These endpoints are available when the backend is started with a database-backed profile such as `postgres`.

## Tax calculator endpoint

`POST /api/calculator/calculate`

The calculator reads relief caps and progressive tax brackets from the selected policy year's database records.
The current schema uses UUID primary keys for relief categories, so `reliefCategoryId` is a UUID string in the API.

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
- Missing policy year values return a validation error and unknown policy years return `404`.

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
