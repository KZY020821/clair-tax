# 11 — Mobile Notifications Plan

## Purpose
Increase retention and prompt timely actions.

## Agent Interpretation

- Notifications are a re-engagement channel tied to clear user actions, not a generic marketing surface.
- Every notification type should be traceable to a backend event or explicit product rule and should map to an understandable destination in the app.
- Favor a small set of high-value notification types before expanding coverage.

## High-value notifications
- receipt processed
- receipt needs review
- filing season reminder
- unused relief reminder
- policy year update

## Rules
- notifications must be opt-in
- every push type should map to a screen destination
- avoid spammy frequency
- all messages must be explainable and actionable

## Payload examples
```json
{
  "type": "RECEIPT_REVIEW_REQUIRED",
  "receiptId": "uuid"
}
```

```json
{
  "type": "SUGGESTION_AVAILABLE",
  "year": 2025
}
```

## Mobile tasks
- register push token
- update token on refresh
- notification tap routing
- settings toggle screen

## Delivery Rule

- Notification support is not complete until opt-in state, token lifecycle, payload routing, and user settings are all coherent.
- Human review should focus on user control, routing correctness, and whether each push is actionable enough to justify interrupting the user.
