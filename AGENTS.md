# AGENTS.md - Project Instructions for AI Agents (TelegramClone)

## ALWAYS READ THESE FILES FIRST (persistent / permanent docs):
1. `@coding-convention.md` → naming rules, formatting, layering, error handling
2. `@project-overview.md` → high-level architecture (hexagonal + lightweight DDD)
3. `@security.md` → JWT, roles, data protection, OWASP Top 10 compliance

## Module-Specific Guidelines (persistent):
- Authentication & User Management: read docs/modules/auth-module.md first
    - Use JWT + Refresh Token, no session-based auth
    - Key services: AuthService, UserService, TokenService
- Messaging & Real-time: read docs/modules/messaging.md
    - Redis pub/sub for events, PostgreSQL for persistence
- Notification: read docs/modules/notification.md
- Media/Storage: read docs/modules/media-module.md (MinIO / S3-compatible)

## Temporary / Transitional Docs (short-lived):
- chat-api.md → Temporary file used to share API structure with frontend team (endpoints, DTOs, WebSocket topics, etc.).
    - If this file still exists: read it when generating or referencing chat-related API code.
    - If this file has been deleted: ignore it completely. Fall back to docs/modules/chat-api.md (or docs/api-spec/ if it exists) as the official source.
    - Rule: Never generate new code based on chat-api.md after it has been removed. Ask the user if unsure.

## Global Rules (apply at all times):
- Tech stack: Java 21, Spring Boot 3.5, PostgreSQL 16, Redis 7, WebSocket/STOMP, JWT (jjwt library)
- Layering: Controller → Service → Repository (hexagonal architecture style)
- Avoid: direct database access in controllers, god classes, cyclic dependencies
- Prefer: records for DTOs, sealed interfaces for state modeling, functional style where it fits
- Testing: unit tests (JUnit 5 + Mockito), integration tests (Testcontainers), security scanning (OWASP ZAP in CI)

## When generating or suggesting code:
- Stick strictly to existing patterns — do not invent new ones
- Add Javadoc only for public APIs and interfaces
- Use clear, meaningful variable names (no abbreviations like usr, req, resp)
- Handle exceptions properly (custom exceptions + global @ControllerAdvice)

## Workflow for Non-Trivial Tasks (e.g., new feature, refactor, bug fix > 50 lines)
ALWAYS follow this step-by-step process:

1. **Plan First** — Do NOT generate any code yet.
    - Create or update a file: `tasks/<task-slug>-plan.md` (e.g., tasks/add-group-chat-plan.md).
    - In that file:
        - Restate the goal and acceptance criteria.
        - Break down into small, sequential tasks (use Markdown checklist: - [ ] Task 1).
        - For each task: describe what to do, which files to touch, potential risks/edge cases.
        - Reference relevant docs (e.g., read docs/modules/messaging.md first).
    - Output only the plan file content and ask: "Review this plan. Approve to proceed? (Reply 'approve', 'edit: ...', or 'reject: ...')"

2. **Wait for Human Approval**
    - Do NOT proceed to code generation until user explicitly says "approve" or similar.
    - If edits needed: user will reply with changes → regenerate plan.

3. **Execute Approved Tasks**
    - One task at a time: implement → test (if possible) → commit suggestion.
    - After each task: ask "Task done. Proceed to next?" or show diff.

4. **Temporary files**
    - Delete or archive plan files after completion if not needed long-term.

This saves tokens and ensures alignment with project rules (hexagonal, security, etc.).

Update this file whenever project conventions change or temporary files are removed/added.