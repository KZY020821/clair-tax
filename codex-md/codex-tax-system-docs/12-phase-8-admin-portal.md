# 12 — Phase 8: Admin Portal

## Objective
Allow tax policy operators to manage yearly brackets, relief categories, content, and publication safely.

## Agent Interpretation

- This phase is an operational control surface, so safety and clarity matter more than UI cleverness.
- Build workflows that make draft editing easy and accidental publish or post-publish mutation difficult.
- Treat audit visibility, validation summaries, and publish confirmations as product requirements, not optional polish.

## Admin Capabilities
- create draft year
- clone previous year
- edit tax brackets
- edit relief categories
- edit explanatory content
- preview policy
- publish policy
- view audit log

## Core Workflow
1. Admin creates draft for new year.
2. Admin clones previous year.
3. Admin applies annual changes.
4. Admin previews static page content.
5. Admin publishes after review.

## Codex Task 1
```md
Implement admin policy list and detail pages.

Requirements:
- List policy years with status
- Add create draft action
- Add clone from previous year action
- Add edit pages for brackets and relief categories
- Add optimistic UI with proper loading/error handling
```

## Codex Task 2
```md
Implement publish flow UI.

Requirements:
- Add policy validation summary panel
- Show publish confirmation modal
- Display publish result and timestamp
- Show publish audit log entries
```

## Codex Task 3
```md
Implement role guard placeholder.

Requirements:
- Restrict admin routes to admin users only
- Add backend route protection hook integration
```

## Review Checklist
- drafts are editable
- published years are clearly marked
- accidental publish is hard
- audit surface is visible

## Dependencies and Handoff

- Depends on policy engine state transitions, auth/role enforcement, and audit event storage.
- Should unblock yearly policy maintenance without requiring direct database edits or code releases for routine rule changes.
- Human review should focus on safety rails, publish-state clarity, and whether the UI clearly distinguishes editable draft data from locked published data.
