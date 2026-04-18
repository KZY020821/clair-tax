---
name: shadcn-ui
description: Use this skill when building Clair Tax frontend components with shadcn/ui and Tailwind CSS, especially for forms, dialogs, cards, tables, filters, and reusable component patterns.
---

# shadcn/ui Skill

## Purpose
Use this skill when the task should follow the project's shadcn/ui style.

## Core rules
- Prefer existing shadcn/ui primitives over new third-party UI libraries.
- Compose from existing components before creating custom wrappers.
- Keep Tailwind class usage intentional and readable.
- Reuse existing utility patterns such as cn() if already present.

## Styling guidance
- Preserve the app's spacing rhythm and typography scale.
- Avoid arbitrary values unless needed.
- Keep dark mode compatibility intact if the project supports it.

## Output expectations
- Use the repository's existing import paths.
- Generate polished component implementations, not just raw markup.
