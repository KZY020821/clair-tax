# 01 — Mobile Product Scope

## Objective
Ship a mobile application for iOS and Android that feels like the Next.js web app in structure and behavior, while using mobile-native patterns for camera, uploads, notifications, and secure session handling.

## Agent Interpretation

- This file defines the product boundary for the mobile app and should be used to prevent scope creep during implementation.
- Mobile is a companion client to the same tax platform, not a separate product with different policy or calculation semantics.
- Treat the listed in-scope items as the intended MVP surface and the out-of-scope items as explicit deferments unless a human reprioritizes them.

## In scope for MVP
- Magic link login with deep link completion
- Dashboard with the same top-level modules as web
- Tax calculator using backend-driven policy data
- Relief tracker with used vs remaining caps
- Receipt capture from camera and gallery
- OCR result review and correction
- AI suggestions page
- Profile and settings
- Push notification registration
- Analytics and crash reporting

## Out of scope for MVP
- Full offline-first sync engine
- Native tablet-specific layouts
- In-app filing submission to government systems
- Full multi-language localization beyond architecture readiness
- Rich admin console inside mobile

## Product rule
The backend remains the source of truth for:
- policy years
- relief categories
- tax brackets
- calculation results
- receipt OCR state
- AI suggestion content

## UX rule
The mobile app must preserve these web concepts:
- same naming of modules
- same order of major steps
- same tax result semantics
- same relief category grouping
- same year selection logic
- same explanation style and disclaimers

## Mobile-specific value
The mobile app should improve:
- receipt capture speed
- upload convenience
- reminder frequency
- re-engagement through push notifications

## Decision Boundaries for Codex

- Do not move tax logic, policy rules, or OCR truth into the device just to make the app feel more autonomous.
- Do not treat missing admin features as a gap in the mobile MVP because rich admin tooling is explicitly out of scope here.
- If native affordances introduce a new flow, keep the same core semantics, statuses, and explanations as the web product.
