---
name: nextjs-app-router
description: Use this skill when working on the Clair Tax Next.js frontend, including routes, layouts, pages, metadata, server components, client components, and data fetching in the App Router.
---

# Next.js App Router Skill

## Purpose
Use this skill for route-level frontend work in Next.js.

## Core rules
- Prefer Server Components by default.
- Use "use client" only when needed.
- Keep client-side state local and minimal.
- Prefer route-local organization for route-specific UI.

## Data fetching
- Prefer server-side fetching when possible.
- Use fetch with explicit cache behavior when freshness matters.
- Avoid unnecessary client-side fetching.

## Route design
- Use loading and error UI when they improve the flow.
- Keep nested layouts intentional.
- Set metadata clearly.

## Output expectations
- Generate complete, runnable code.
- Match existing folder and naming patterns.
- Include imports.
