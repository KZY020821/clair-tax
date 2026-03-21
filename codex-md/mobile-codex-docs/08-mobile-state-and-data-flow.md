# 08 — Mobile State and Data Flow

## Global state
Keep global state small:
- auth session
- selected tax year
- theme / preferences
- pending upload queue metadata

## Agent Interpretation

- This file defines state ownership boundaries so the app does not drift into duplicate sources of truth.
- Treat server data, device-only state, and ephemeral UI state as separate concerns with explicit ownership.
- Prefer predictable state transitions over clever local caching, especially around auth and receipt upload workflows.

## Server state
Use TanStack Query for:
- current user
- policy year data
- calculations
- receipt list
- receipt detail
- suggestions

## Cache policy
- policies: long cache, invalidated when year changes
- calculations: short cache
- receipts list: refetch on screen focus
- suggestions: refetch daily or on manual refresh

## Upload state machine
Local states:
- selected
- compressing
- requestingUploadUrl
- uploading
- uploaded
- processing
- needsReview
- confirmed
- failed

## Error handling
Every API call needs:
- friendly error message
- retry path
- analytics event for failure

## Rule for Codex
Codex must not mix local component state and server cache in a way that causes duplicated sources of truth.

## State Ownership Reminder

- Persist only the device state that must survive app restarts, such as secure session data and resumable upload metadata.
- Query cache should own backend-derived entities like policies, calculations, receipts, and suggestions.
- Component state should stay local to presentation concerns like open accordions, field dirtiness, and transient form interaction.
