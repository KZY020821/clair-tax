---
name: springboot-service
description: Use this skill when implementing or modifying Clair Tax Spring Boot services, controllers, configuration, dependency wiring, validation, domain logic, or persistence-facing application code.
---

# Spring Boot Service Skill

## Purpose
Use this skill for Java backend work in Spring Boot.

## Core rules
- Keep controllers thin and focused on HTTP concerns.
- Keep business rules in services or domain logic.
- Prefer constructor injection.
- Use explicit DTOs where they improve clarity.
- Keep configuration predictable.

## Error handling
- Return useful API-level errors.
- Do not leak stack traces to clients.
- Validate requests at boundaries.

## Output expectations
- Generate code that matches existing package structure and naming.
- Prefer production-ready implementations over placeholders.
