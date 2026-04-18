---
name: flyway-postgres
description: Use this skill when working on Clair Tax PostgreSQL schema changes, Flyway migrations, entity-to-schema alignment, SQL compatibility, and persistence-safe backend updates.
---

# Flyway + PostgreSQL Skill

## Purpose
Use this skill for schema and migration work.

## Core rules
- Every schema change should be migration-backed.
- Keep migrations deterministic and incremental.
- Do not rewrite old applied migrations unless the task explicitly requires it and the environment permits it.
- Be cautious with destructive changes.

## Schema guidance
- Keep table and column naming consistent with existing patterns.
- Align entity meaning with schema meaning.
- Be explicit about nullability, uniqueness, and foreign keys.

## Safety
- Consider existing data when changing constraints.
- Prefer additive migration paths where practical.

## Output expectations
- Include migration files or exact migration contents when schema changes are part of the task.
