# Database Schema

## Table: `users`

Stores user account information.

| Column            | Type                     | Constraints              | Description                                |
|------------------|--------------------------|--------------------------|--------------------------------------------|
| `id`             | UUID                     | PRIMARY KEY              | Unique user identifier (generated)         |
| `username`       | VARCHAR                  | NOT NULL, UNIQUE         | Unique username                            |
| `display_name`   | VARCHAR                  |                          | Display name (can be null)                 |
| `phone`          | VARCHAR                  | UNIQUE                   | Phone number (unique, can be null)         |
| `email`          | VARCHAR                  | UNIQUE                   | Email address (unique, can be null)        |
| `password_hash`  | VARCHAR                  | NOT NULL                 | BCrypt-hashed password                     |
| `avatar_media_id`| UUID                     |                          | Optional avatar media id                   |
| `bio`            | TEXT                     |                          | Short user biography                       |
| `role`           | VARCHAR(20)              | NOT NULL                 | User role: `USER`, `ADMIN`, `MODERATOR`; default `USER` |
| `created_at`     | TIMESTAMP WITH TIME ZONE | NOT NULL                 | Account creation timestamp                 |
| `updated_at`     | TIMESTAMP WITH TIME ZONE | NOT NULL                 | Last update timestamp                      |
| `last_seen`      | TIMESTAMP                |                          | Last activity timestamp (for online status) |

**Indexes:**
- Primary key on `id`
- Unique constraints on `username`, `email`, `phone`
- Recommended additional indexes: `(email)`, `(username)`, `(last_seen)`

---

## Table: `refresh_tokens`

Manages refresh tokens for authentication.

| Column       | Type       | Constraints                             | Description                                      |
|--------------|------------|-----------------------------------------|--------------------------------------------------|
| `token`      | VARCHAR(36)| PRIMARY KEY                             | Refresh token value (UUID string)                |
| `user_id`    | UUID       | NOT NULL, FOREIGN KEY (users.id)        | Owner of the token                               |
| `expires_at` | TIMESTAMP  | NOT NULL                                | Expiration timestamp                             |
| `created_at` | TIMESTAMP  | NOT NULL                                | Creation timestamp                               |
| `revoked`    | BOOLEAN    | NOT NULL, DEFAULT FALSE                 | Flag indicating whether token has been revoked   |

**Indexes:**
- `idx_user_revoked` on `(user_id, revoked)` - for fast lookup of active tokens per user
- `idx_created_at` on `(created_at)` - for cleanup jobs

**Notes:**
- Token is stored as plain UUID; consider hashing for production (BCrypt) to avoid DB leakage.
- Expired tokens should be periodically cleaned up.

---

## Table: `contacts`

Represents relationships between users (friends, contacts).

| Column        | Type      | Constraints                             | Description                                      |
|---------------|-----------|-----------------------------------------|--------------------------------------------------|
| `owner_id`    | UUID      | NOT NULL, FOREIGN KEY (users.id)        | User who owns the contact list                   |
| `contact_id`  | UUID      | NOT NULL, FOREIGN KEY (users.id)        | User being added as contact                      |
| `is_muted`    | BOOLEAN   | NOT NULL, DEFAULT FALSE                 | Whether notifications from this contact are muted|
| `is_blocked`  | BOOLEAN   | NOT NULL, DEFAULT FALSE                 | Whether this contact is blocked                  |
| `alias`       | TEXT      |                                         | Custom display name for this contact (nullable)  |
| `created_at`  | TIMESTAMP | NOT NULL                                | When the contact relationship was created        |

**Primary Key:** `(owner_id, contact_id)` (composite key)

**Indexes:**
- Composite primary key automatically indexed.
- Recommended additional indexes: `(owner_id)`, `(contact_id)`, `(owner_id, is_muted)`.

**Notes:**
- A user cannot have duplicate contacts (enforced by composite key).
- `owner_id` and `contact_id` are both foreign keys to `users(id)`.

---

## Table: `conversations`

Represents chat threads (private, group, channel).

| Column       | Type      | Constraints                             | Description                                      |
|--------------|-----------|-----------------------------------------|--------------------------------------------------|
| `id`         | UUID      | PRIMARY KEY                             | Unique conversation identifier                   |
| `type`       | VARCHAR   | NOT NULL                                | Conversation type: `PRIVATE`, `GROUP`, `CHANNEL` |
| `title`      | VARCHAR   |                                         | Conversation title (optional)                    |
| `created_at` | TIMESTAMP | NOT NULL                                | Creation timestamp                               |
| `updated_at` | TIMESTAMP | NOT NULL                                | Last update timestamp                            |

---

## Table: `conversation_members`

Memberships linking users to conversations.

| Column            | Type      | Constraints                              | Description                                      |
|-------------------|-----------|------------------------------------------|--------------------------------------------------|
| `conversation_id` | UUID      | NOT NULL, FOREIGN KEY (conversations.id) | Conversation                                     |
| `user_id`         | UUID      | NOT NULL, FOREIGN KEY (users.id)         | Member user                                      |
| `role`            | VARCHAR   | NOT NULL                                 | Role in conversation: `MEMBER`, `ADMIN`, `OWNER` |
| `joined_at`       | TIMESTAMP | NOT NULL                                 | When user joined                                 |

**Primary Key:** `(conversation_id, user_id)` (composite key)

---

## Table: `messages`

Chat messages within a conversation.

| Column            | Type      | Constraints                              | Description                                      |
|-------------------|-----------|------------------------------------------|--------------------------------------------------|
| `id`              | BIGINT    | PRIMARY KEY, GENERATED (IDENTITY)        | Message identifier                               |
| `conversation_id` | UUID      | NOT NULL, FOREIGN KEY (conversations.id) | Conversation                                     |
| `sender_id`       | UUID      | FOREIGN KEY (users.id)                   | Sender (nullable for system events)              |
| `message_type`    | VARCHAR   | NOT NULL                                 | `TEXT`, `IMAGE`, `VIDEO`, `FILE`, `SYSTEM`       |
| `body`            | TEXT      |                                          | Message body                                     |
| `deleted`         | BOOLEAN   | NOT NULL, DEFAULT FALSE                  | Soft delete flag                                 |
| `created_at`      | TIMESTAMP | NOT NULL                                 | Creation timestamp                               |

**Indexes:**
- Recommended index on `(conversation_id, id)` for pagination.

---

## Table: `media`

Stores media files (images, videos, documents) with metadata.

| Column            | Type      | Constraints                  | Description                                      |
|-------------------|-----------|------------------------------|--------------------------------------------------|
| `id`              | UUID      | PRIMARY KEY                  | Unique media identifier                          |
| `owner_id`        | UUID      | FOREIGN KEY (users.id)       | User who uploaded the media                      |
| `storage_key`     | VARCHAR   | NOT NULL                     | Key/path in Cloudinary storage                   |
| `url`             | VARCHAR   | NOT NULL                     | Full URL to access the media                     |
| `resource_type`   | VARCHAR   | NOT NULL                     | Media type: `image`, `video`, `raw` (file)       |
| `mime_type`       | VARCHAR   |                              | MIME type (e.g., `image/png`, `video/mp4`)       |
| `file_name`       | VARCHAR   |                              | Original file name                               |
| `file_size`       | BIGINT    |                              | File size in bytes                               |
| `width`           | INTEGER   |                              | Image/video width (nullable for non-visual)      |
| `height`          | INTEGER   |                              | Image/video height (nullable for non-visual)     |
| `duration`        | DECIMAL   |                              | Duration in seconds (for videos/audio)           |
| `status`          | VARCHAR   | NOT NULL                     | `TEMP`, `PERSISTED` (lifecycle state)            |
| `created_at`      | TIMESTAMP | NOT NULL                     | When media was uploaded                          |
| `updated_at`      | TIMESTAMP | NOT NULL                     | Last update timestamp                            |

**Indexes:**
- Recommended index on `(owner_id)` for user media lookup
- Recommended index on `(status, created_at)` for cleanup queries

**Notes:**
- `TEMP` status: Media uploaded but not yet attached to a message
- `PERSISTED` status: Media is actively used in messages or profiles
- Cleanup job should remove `TEMP` media after TTL (e.g., 24 hours)

---

## Table: `message_media`

Junction table linking messages to media attachments.

| Column            | Type      | Constraints                              | Description                                      |
|-------------------|-----------|------------------------------------------|--------------------------------------------------|
| `message_id`      | BIGINT    | NOT NULL, FOREIGN KEY (messages.id)      | Message                                          |
| `media_id`        | UUID      | NOT NULL, FOREIGN KEY (media.id)         | Attached media                                   |
| `ordinal`         | INTEGER   | NOT NULL                                 | Display order (1-based)                          |

**Primary Key:** `(message_id, media_id)` (composite key)

**Notes:**
- A message can have multiple media attachments (ordered by `ordinal`)
- A media file can be referenced by multiple messages (e.g., forwarded message with attachment)

---

## Table: `unread_counters`

Tracks last read message per user per conversation.

| Column                 | Type      | Constraints                              | Description                                      |
|------------------------|-----------|------------------------------------------|--------------------------------------------------|
| `conversation_id`      | UUID      | NOT NULL, FOREIGN KEY (conversations.id) | Conversation                                     |
| `user_id`              | UUID      | NOT NULL, FOREIGN KEY (users.id)         | User                                             |
| `last_read_message_id` | BIGINT    |                                          | Last read message id                             |
| `updated_at`           | TIMESTAMP | NOT NULL                                 | Last update timestamp                            |

**Primary Key:** `(conversation_id, user_id)` (composite key)

---

## Enum: `RoleUser`

Defines possible user roles.

| Value       | Description                          |
|-------------|--------------------------------------|
| `USER`      | Regular user (default)               |
| `ADMIN`     | System administrator                 |
| `MODERATOR` | Moderator (e.g., for channels/groups)|

Stored as a string in the `role` column of `users`.

---

## Enum: `ConversationType`

Defines possible conversation types.

| Value     | Description      |
|-----------|------------------|
| `PRIVATE` | 1:1 chat         |
| `GROUP`   | Group chat       |
| `CHANNEL` | Broadcast channel|

Stored as a string in the `type` column of `conversations`.

---

## Enum: `ConversationRole`

Defines possible conversation member roles.

| Value    | Description        |
|----------|--------------------|
| `MEMBER` | Standard member    |
| `ADMIN`  | Admin member       |
| `OWNER`  | Conversation owner |

Stored as a string in the `role` column of `conversation_members`.

---

## Enum: `MessageType`

Defines possible message types.

| Value    | Description      |
|----------|------------------|
| `TEXT`   | Plain text       |
| `IMAGE`  | Image attachment |
| `VIDEO`  | Video attachment |
| `FILE`   | File attachment  |
| `SYSTEM` | System event     |

Stored as a string in the `message_type` column of `messages`.

---

## Relationships

- **One-to-Many**: A `User` can have many `RefreshToken`s.
- **One-to-Many**: A `User` can be the `owner` of many `Contact` records.
- **One-to-Many**: A `User` can be the `contact` in many `Contact` records (i.e., appear in others' contact lists).
- **One-to-Many**: A `Conversation` can have many `Message`s.
- **Many-to-Many (via join table)**: Users are members of conversations through `conversation_members`.
- **One-to-Many**: A `Conversation` can have many `ConversationMember`s.
- **One-to-Many**: A `User` can have many `Message`s as sender.
- **One-to-Many**: A `User` can have many `Media` files (as owner).
- **One-to-Many**: A `Message` can have many `MessageMedia` attachments.
- **Many-to-Many (via join table)**: Messages are linked to media files through `message_media`.
- **One-to-One (per conversation per user)**: `UnreadCounter` tracks the last read message for a user in a conversation.

---

## Notes for Implementation

- All tables use `TIMESTAMP` or `TIMESTAMP WITH TIME ZONE` for time fields (adjust based on database).
- Foreign keys are logical; actual constraints may be omitted in some JPA setups but are recommended.
- `created_at`/`updated_at` are managed via JPA lifecycle callbacks or Hibernate timestamps.
- Use batch cleanup jobs to remove expired `refresh_tokens` and soft-deleted data.

---

*This schema reflects the current entity design as of 2026-03-21.*
