# User Module

## Responsibilities
- Retrieve current authenticated user profile (`GET /api/users/me`).
- Fetch public profile of another user by ID (`GET /api/users/{userId}`).
- Update profile details: display name, bio, and avatar media ID (`PUT /api/users/profile`).
- Global user search by keyword (username, display name, email, or phone) excluding self (`GET /api/users/search?query=...`).

## Key Models & Enums
- **`User` Entity:** Stores core identity (`id`, `username`, `email`, `phone`, `password_hash`, `display_name`, `bio`, `avatar_media_id`, `role`, `created_at`, `updated_at`, `last_seen`).
- **`RoleUser` Enum:**
  - `USER`: Regular platform user (default).
  - `MODERATOR`: System moderator.
  - `ADMIN`: System administrator.

## Rules & Integration
1. **Security Guard:** Profile updates are limited strictly to the authenticated principal (`SecurityContext`).
2. **Avatar Media Integration:** Avatars are uploaded via `/api/media/upload` first. Passing the resulting `avatarMediaId` to profile update binds the media lifecycle to `PERSISTED`.
3. **Search Constraints:** User search excludes the calling user and returns sanitized public DTOs without sensitive fields (password hash, internal tokens).