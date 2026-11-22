# AGENTS.md - Project Instructions for AI Agents (TelegramClone)

## ALWAYS READ THESE FILES FIRST (when relevant to the task):
1. `.agents/rules/coding_conventions.md` → naming rules, formatting, layering, error handling
2. `.agents/rules/architecture_and_security.md` → layer boundaries, JWT, validation
3. `docs/project-overview.md` → system architecture & package layout
4. `docs/security.md` → JWT + Refresh Token rotation, CORS, authorization rules

## Module-Specific Guidelines:
- Authentication & User Management: read `docs/modules/auth-module.md` and `docs/modules/user-module.md`
  - JWT access token + Refresh Token cookie, no server session
  - Key components: `AuthService`, `UserService`, `RefreshTokenService`, `JwtTokenProvider`
- Messaging & Real-time: read `docs/modules/messaging-module.md` and `docs/modules/chat-api.md`
  - PostgreSQL for persistence; Redis for presence, typing, slow mode, and view deduplication
  - Domain events + `@TransactionalEventListener(AFTER_COMMIT)` → `ChatBroadcaster` (`StompChatBroadcaster`) for WebSocket broadcast
- Contacts / Presence / Invite Links: read `docs/modules/contact-module.md`, `docs/modules/presence-module.md`, `docs/modules/invite-link-module.md`
- Media/Storage: Cloudinary (`MediaService`, `CloudinaryConfig`)

## Global Rules (apply at all times):
- Tech stack: Java 21, Spring Boot 3.5, PostgreSQL 16, Redis 7, WebSocket/STOMP, JWT (`jjwt`), MapStruct
- Layering: `Controller` → `Service` (`I{Domain}Service` + `{Domain}Service`) → `Repository` (Layered Monolith + Internal Event-Driven)
- Avoid: direct database/WebSocket logic in controllers, broadcasting inside open `@Transactional`, god classes, cyclic dependencies
- Prefer: Java `record` for DTOs, `@Transactional` only in service layer (`readOnly = true` for queries), `ChatBroadcaster` + `AFTER_COMMIT` listeners for real-time events
- Testing: unit & slice tests with JUnit 5 + Mockito + Spring Boot Test

## When generating or suggesting code:
- Stick strictly to existing patterns — do not invent new ones
- Add Javadoc only for public APIs and interfaces
- Use clear, meaningful variable names (no abbreviations like `usr`, `req`, `resp`)
- Handle exceptions via `BusinessException` + `ErrorCode` + `GlobalExceptionHandler`

## Workflow for Non-Trivial Tasks (e.g., new feature, refactor, bug fix > 50 lines):
1. **Plan First (in chat, NO file creation)** — Do NOT generate application code or create files in `tasks/` unless the user explicitly asks to save a file.
   - Present the plan directly in your chat reply, including:
     - Goal and acceptance criteria
     - Sequential checklist (`- [ ] Task 1`, ...) with files to touch and edge cases/risks
     - Relevant docs/modules referenced
   - End by asking the user to reply `approve`, `edit: ...`, or `reject: ...`.
2. **Wait for Human Approval**
   - Do NOT edit or create code files until the user explicitly approves.
3. **Execute Approved Tasks**
   - Implement step by step according to the approved plan, verify with relevant tests, and report what changed.

Update this file whenever project conventions or workflows change.
