# Clair Tax Mobile App

Expo Router mobile client for Clair Tax. The app mirrors the existing `/frontend`
workspace structure and reuses the same backend and AI service contracts.

## Scripts

- `bun run dev`
- `bun run android`
- `bun run ios`
- `bun run web`
- `bun run typecheck`

## Environment

- `EXPO_PUBLIC_API_BASE_URL`
- `EXPO_PUBLIC_AI_SERVICE_BASE_URL`

If unset, the app defaults to:

- backend: `http://<detected-dev-host>:8080`
- AI service: `http://<detected-dev-host>:8000`

For local Expo development, the app now auto-detects the host machine IP from the
Expo dev session. On Android emulators it falls back to `10.0.2.2` and on iOS/web
it falls back to `127.0.0.1` when host detection is unavailable.

## Notes

- Routing uses Expo Router under `app/`.
- Server state uses TanStack Query.
- Receipt upload supports both rear-camera capture and local file selection for images, while keeping PDF uploads available through the native document picker.
