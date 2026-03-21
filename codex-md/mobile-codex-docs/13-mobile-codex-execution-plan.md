# 13 — Mobile Codex Execution Plan

## Principle
Assign small, reviewable tasks to Codex.

## Agent Interpretation

- This sequence is a dependency-aware rollout plan for mobile delivery, not a mandate to batch several numbered steps into one implementation request.
- Use it to choose the next safe task after review, keeping auth, navigation, calculator, and receipt work isolated where possible.
- If the repo already contains part of this sequence, resume from the earliest unfinished dependency instead of restarting from phase 1.

## Sequence

### Phase 1 — foundation
1. Scaffold Expo TypeScript app
2. Add Expo Router
3. Add lint, format, TypeScript strict config
4. Create app theme aligned with web
5. Add API client and environment config

### Phase 2 — auth
6. Implement login screen
7. Add deep link config
8. Implement verify flow
9. Add secure token storage
10. Add logout and refresh flow

### Phase 3 — navigation and dashboard
11. Build bottom tabs
12. Build home dashboard cards
13. Add shared current-year state

### Phase 4 — calculator
14. Build calculator form shell
15. Integrate policy endpoint
16. Render backend-driven relief fields
17. Build results summary
18. Save and restore latest result

### Phase 5 — receipts
19. Build receipts list
20. Add camera/file picker
21. Add upload URL request flow
22. Add direct S3 upload
23. Add OCR review screen
24. Add error and retry handling

### Phase 6 — suggestions
25. Build suggestions list
26. Build potential savings cards
27. Add deep links from suggestions to calculator categories

### Phase 7 — release
28. Add push token registration
29. Add analytics and crash monitoring
30. Add tests
31. Prepare staging build
32. Prepare production release checklist

## Review rule
No Codex task should touch more than one of:
- navigation shell
- auth
- calculator
- receipts
- suggestions
unless explicitly intended.

## Task Sizing Reminder

- Foundation tasks can set up shared plumbing, but feature tasks should still stay focused on one user workflow at a time.
- When a change crosses these boundaries, document why the combined scope is safer than splitting it.
