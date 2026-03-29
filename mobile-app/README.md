# Clair Tax Mobile App

Expo Router mobile client for Clair Tax. The app mirrors the existing `/frontend`
workspace structure and reuses the same backend and AI service contracts.

## Scripts

- `bun run dev`
- `bun run android`
- `bun run ios`
- `bun run ios:preflight`
- `bun run ios:sim`
- `bun run web`
- `bun run typecheck`

## Environment

- `EXPO_PUBLIC_API_BASE_URL`
- `EXPO_PUBLIC_AI_SERVICE_BASE_URL`
- `CLAIR_IOS_SIMULATOR_UDID`
- `CLAIR_IOS_METRO_PORT`

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
- For local iPhone Simulator work against a non-`local` backend profile, first run `EXPO_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 bun run ios:preflight`.
- If the preflight passes, launch the app with `EXPO_PUBLIC_API_BASE_URL=http://127.0.0.1:8080 bun run ios:sim`.
- The preflight checks the active Xcode iOS Simulator SDK version, requires an exact matching installed iOS simulator runtime, and requires at least one matching available iPhone simulator before Expo starts.
- `CLAIR_IOS_SIMULATOR_UDID` can pin a specific simulator when multiple matching iPhones exist.
- `CLAIR_IOS_METRO_PORT` overrides the default Metro port of `8082` used by `bun run ios:sim`.
- `expo run:ios` keeps the app on the managed Expo path. If a failed prebuild leaves a generated `ios/` folder behind, remove that transient folder before retrying so the next prebuild starts clean.
