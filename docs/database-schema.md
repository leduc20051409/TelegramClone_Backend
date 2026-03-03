# Database Schema

## Table: `users`

Stores user account information.

| Column          | Type           | Constraints                    | Description                                |
|-----------------|----------------|--------------------------------|--------------------------------------------|
| `id`            | UUID           | PRIMARY KEY                    | Unique user identifier (generated)         |
| `username`      | VARCHAR        | NOT NULL, UNIQUE               | Unique username                            |
| `display_name`  | VARCHAR        |                                | Display name (can be null)                  |
| `phone`         | VARCHAR        | UNIQUE                         | Phone number (unique, can be null)          |
| `email`         | VARCHAR        | UNIQUE                         | Email address (unique, can be null)         |
| `password_hash` | VARCHAR        | NOT NULL                       | BCrypt-hashed password                      |
| `avatar_media_id`| UUID          |                                | Reference to media ID (foreign key to media table) |
| `bio`           | TEXT           |                                | Short user biography                        |
| `role`          | VARCHAR(20)    | NOT NULL                       | User role: `USER`, `ADMIN`, `MODERATOR`; default `USER` |
| `created_at`    | TIMESTAMP WITH TIME ZONE | NOT NULL            | Account creation timestamp                  |
| `updated_at`    | TIMESTAMP WITH TIME ZONE | NOT NULL            | Last update timestamp                       |
| `last_seen`     | TIMESTAMP      |                                | Last activity timestamp (for online status) |

**Indexes:**
- Primary key on `id`
- Unique constraints on `username`, `email`, `phone`
- Recommended additional indexes: `(email)`, `(username)`, `(last_seen)`

---

## Table: `refresh_tokens`

Manages refresh tokens for authentication.

| Column       | Type      | Constraints                             | Description                                      |
|--------------|-----------|-----------------------------------------|--------------------------------------------------|
| `token`      | VARCHAR(36)| PRIMARY KEY                            | Refresh token value (UUID string)                |
| `user_id`    | UUID      | NOT NULL, FOREIGN KEY (users.id)       | Owner of the token                               |
| `expires_at` | TIMESTAMP | NOT NULL                                | Expiration timestamp                             |
| `created_at` | TIMESTAMP | NOT NULL                                | Creation timestamp                               |
| `revoked`    | BOOLEAN   | NOT NULL, DEFAULT FALSE                 | Flag indicating whether token has been revoked   |

**Indexes:**
- `idx_user_revoked` on `(user_id, revoked)` – for fast lookup of active tokens per user
- `idx_created_at` on `(created_at)` – for cleanup jobs

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
| `is_muted`    | BOOLEAN   | NOT NULL, DEFAULT FALSE                  | Whether notifications from this contact are muted|
| `is_blocked`  | BOOLEAN   | NOT NULL, DEFAULT FALSE                  | Whether this contact is blocked                   |
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

## Enum: `RoleUser`

Defines possible user roles.

| Value       | Description                          |
|-------------|--------------------------------------|
| `USER`      | Regular user (default)               |
| `ADMIN`     | System administrator                 |
| `MODERATOR` | Moderator (e.g., for channels/groups)|

Stored as a string in the `role` column of `users`.

---

## Relationships

- **One-to-Many**: A `User` can have many `RefreshToken`s.
- **One-to-Many**: A `User` can be the `owner` of many `Contact` records.
- **One-to-Many**: A `User` can be the `contact` in many `Contact` records (i.e., appear in others' contact lists).

---

## Notes for Implementation

- All tables use `TIMESTAMP` or `TIMESTAMP WITH TIME ZONE` for time fields (adjust based on database).
- Foreign keys are logical; actual constraints may be omitted in some JPA setups but are recommended.
- For `avatar_media_id`, if a media service exists, consider adding a foreign key constraint to a `media` table.
- Use batch cleanup jobs to remove expired `refresh_tokens` and soft-deleted data.

---

*This schema reflects the current entity design as of [current date].*