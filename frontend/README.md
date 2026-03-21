# Frontend

Minimal Next.js App Router scaffold for the Clair Tax web client.

## Stack

- Next.js
- TypeScript
- TailwindCSS
- Bun
- TanStack Query
- Zod

## Local setup

1. Install dependencies:
   `bun install`
2. Start the development server:
   `bun run dev`
3. Open [http://localhost:3000](http://localhost:3000)

If the backend is not running on `http://localhost:8080`, set:
`NEXT_PUBLIC_API_BASE_URL=http://localhost:18080`

If the AI service is not running on `http://localhost:8000`, set:
`NEXT_PUBLIC_AI_SERVICE_BASE_URL=http://localhost:8001`

## Available scripts

- `bun run dev`
- `bun run build`
- `bun run start`
- `bun run lint`

## Current scope

The home page renders `Tax Relief Vault MY` and fetches both seeded `policy_year` rows from Spring Boot and a demo summary from FastAPI with TanStack Query.

## Calculator page

- Route: `/calculator`
- Backend endpoints used:
  - `GET /api/policy-years` for year selector options
  - `GET /api/policies/{year}` for relief category data
  - `POST /api/calculator/calculate` for the calculation result

The calculator page uses `NEXT_PUBLIC_API_BASE_URL` for backend requests.
If the backend is not running on `http://localhost:8080`, set:
`NEXT_PUBLIC_API_BASE_URL=http://localhost:18080`

The UI keeps calculator form state locally, fetches policy data with TanStack Query, and renders the backend's returned summary and tax breakdown without duplicating tax rules in the frontend.
