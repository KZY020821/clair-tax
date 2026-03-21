# 03 — Mobile Architecture

## Recommended stack
- React Native
- Expo
- TypeScript
- Expo Router
- TanStack Query
- Zustand or minimal local UI store
- Expo SecureStore
- Sentry

## Agent Interpretation

- This architecture keeps the mobile app thin on business rules and strong on client orchestration, session handling, and device capabilities.
- Use the recommended stack as the baseline because it supports fast iteration, deep-linking, secure storage, and OTA-friendly mobile workflows.
- If the actual repo uses equivalent libraries, preserve the same layer boundaries and source-of-truth rules rather than forcing exact package matches.

## Backend reuse
Reuse the existing Spring Boot backend and extend it with mobile-oriented auth, upload, and push token endpoints.

## Architecture diagram
```text
React Native App
   |
   +-- Expo Router
   +-- UI Components
   +-- Query Layer
   +-- Secure Session Store
   |
   v
Spring Boot API
   |
   +-- PostgreSQL
   +-- Redis
   +-- S3
   +-- SQS
   +-- OCR / AI service
```

## App layers

### 1. Presentation layer
- screens
- reusable components
- design tokens
- form inputs

### 2. Feature layer
- auth
- calculator
- relief tracker
- receipts
- suggestions
- profile

### 3. Data layer
- API client
- query hooks
- DTO mappers
- cache policy
- upload service

## Principle
Do not put tax business rules in the mobile app.
Only put:
- display logic
- input validation
- local pending upload state
- session state

## Layer Boundary Reminder

- Presentation owns screens and interaction polish.
- Feature modules coordinate user workflows.
- Data services own API contracts, query behavior, secure session persistence, and upload orchestration.
- Backend remains responsible for policy, calculation truth, OCR review truth, and persisted tax data.
