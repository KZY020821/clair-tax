# 07 — Mobile Screen Spec

## Navigation
Bottom tabs:
- Home
- Calculator
- Receipts
- Suggestions
- Profile

## Agent Interpretation

- This file defines screen inventory and expected responsibilities per screen, not exact visual design.
- Use it to keep route coverage complete and prevent features from disappearing into overloaded screens.
- When a feature needs multiple intermediate states, keep those states within the screen’s described responsibility instead of inventing a different navigation model by default.

## Screen list

### Splash
- checks stored session
- routes to auth or app

### Login
- email input
- send magic link button
- privacy note

### Verify Link
- consumes deep link token
- exchanges for session
- shows loading and error states

### Home Dashboard
Cards should mirror web modules.
Sections:
- current tax year snapshot
- recent receipts
- remaining relief highlights
- suggestion preview

### Calculator
Sections:
- year selector
- income form
- relief accordion groups
- result summary
- bracket breakdown

### Receipts List
- filter by year
- filter by status
- scan new receipt CTA
- row card with amount/date/category/status

### Receipt Review
- image preview
- extracted amount
- extracted date
- suggested category
- confirm or edit

### Suggestions
- cards sorted by potential impact
- explanation text
- go-to-category CTA

### Profile
- email
- active year
- notification settings
- retention info
- logout

## UX constraints
- no screen should require horizontal scrolling
- primary actions stay within thumb-friendly area
- screen titles and labels should match web wording

## Screen Delivery Rule

- Each implemented screen should include loading, error, and empty or fallback behavior where relevant.
- If a screen depends on a backend contract that does not exist yet, prefer a placeholder state that preserves the planned screen ownership and navigation path.
