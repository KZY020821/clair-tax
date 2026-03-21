# 02 — UX Parity Principles

## Goal
Maintain a similar user experience between Next.js web and mobile, without forcing desktop interaction patterns onto a phone.

## Agent Interpretation

- Parity here means preserving information architecture, user meaning, and result semantics, not copying layout pixel-for-pixel.
- If a mobile-native interaction changes the shape of a screen, it should still preserve the same labels, statuses, sequence, and explanation model as the web app.
- Use this file to resolve uncertainty when a design choice could improve mobile ergonomics but risks changing product meaning.

## Parity means
- identical information architecture
- same user journey for critical workflows
- same labels, terms, and statuses
- same calculation logic and output fields
- same card hierarchy for dashboard modules
- same success and error meanings

## Where parity is required

### Authentication
Web and mobile both use magic link.
- web: email -> browser verify -> session
- mobile: email -> email deep link -> app verify -> session

### Dashboard
Dashboard tiles should map one-to-one:
- Tax Calculator
- Relief Tracker
- Receipts
- AI Suggestions
- Profile / Settings

### Calculator
The calculation flow must preserve:
1. choose year
2. enter gross income
3. enter relief amounts
4. see tax breakdown
5. save result

### Relief tracker
Use the same grouping and same max-cap language as web.

### Receipt review
Use the same states:
- uploaded
- processing
- needs review
- confirmed
- failed

## Where mobile can differ
- bottom tab navigation instead of web sidebar
- stepper or accordion for long forms
- camera-first receipt upload
- native share sheet or file picker
- toast/snackbar patterns aligned to mobile

## UX parity checklist
- labels match web
- route hierarchy maps to same feature tree
- empty states match web meaning
- calculators produce identical totals for same input
- relief remaining amount matches web for same user and year
- receipt status language is identical
- AI suggestion cards express the same recommendation logic

## Acceptable Divergence Rule

- Mobile may change navigation chrome, gesture patterns, camera/file entry points, and screen density.
- Mobile should not silently change terminology, workflow order, validation meaning, or business result interpretation.
