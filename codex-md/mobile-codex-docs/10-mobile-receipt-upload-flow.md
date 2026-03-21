# 10 — Mobile Receipt Upload Flow

## Why mobile matters most here
Receipt capture is the main reason users will prefer the phone app over web.

## Agent Interpretation

- This is the most device-native mobile workflow and should feel fast, resilient, and interruption-tolerant.
- Treat upload orchestration, background interruption handling, and review clarity as part of the core feature, not secondary polish.
- Keep the backend as the source of truth for upload completion and OCR state, while the device owns short-lived draft and progress state.

## Flow
1. user taps Scan Receipt
2. app opens camera or file picker
3. app compresses image if needed
4. app asks backend for pre-signed upload URL
5. app uploads directly to S3
6. app calls complete endpoint
7. backend writes metadata and queues OCR
8. user sees processing state
9. user reviews extracted data
10. user confirms category and amount

## UX requirements
- show progress
- recover from network failure
- do not lose draft if app backgrounds mid-flow
- allow manual correction of amount/date/category
- show original image on review screen

## Edge cases
- blurry image
- duplicate receipt
- OCR fails
- category not recognized
- upload interrupted

## Codex implementation order
1. picker/camera screen
2. compression helper
3. upload URL integration
4. direct upload service
5. receipt list refresh
6. review form

## Source-of-Truth Rule

- Local state may track pending files and progress, but confirmed receipt lifecycle states should come from backend responses.
- User edits to extracted fields should always remain possible even when OCR confidence is high.
- Human review should focus on resume behavior, data-loss prevention, duplicate handling, and clarity of receipt status transitions.
