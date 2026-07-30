# Invite Link Module

## Responsibilities
- Generate dynamic invitation URLs/tokens for Group chats and Channels (`ConversationInviteLinkController`, `ConversationInviteLinkService`).
- Validate link token expiration, usage limits, and revocation status before allowing users to join.
- Provide public preview info (Group title, description, member count, avatar) for prospective members before joining.
- Enable Admins/Owners to list and revoke active invite links.

## Model Structure (`ConversationInviteLink`)
| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary Key |
| `conversation_id` | UUID | Associated Group/Channel |
| `token` | VARCHAR | Unique URL token string |
| `created_by_id` | UUID | User ID who generated the link |
| `expire_at` | TIMESTAMP | Expiration timestamp (nullable for permanent links) |
| `usage_limit` | INT | Maximum allowed joins (nullable for unlimited) |
| `used_count` | INT | Current join count |
| `is_revoked` | BOOLEAN | Flag indicating manual revocation |
| `created_at` | TIMESTAMP | Creation timestamp |

## Key REST Endpoints (`/api/invite-links`)
- `POST /api/invite-links/generate`: Create a new link with custom `expireAt` and `usageLimit`.
- `GET /api/invite-links/{conversationId}`: Retrieve active links created for a conversation.
- `DELETE /api/invite-links/{linkId}`: Instantly revoke an invite link.
- `GET /api/invite-links/info/{token}`: Fetch public group metadata for link preview screen (No membership required).
- `POST /api/invite-links/join/{token}`: Validate token and add current user as a `MEMBER` to the conversation.

## Security Rules
1. **Permission Check:** Only conversation `ADMIN` or `OWNER` can generate or revoke invite links.
2. **Token Validation:** Joining fails with `400 Bad Request` or `404 Not Found` if the token is revoked (`is_revoked = true`), expired (`expire_at < now`), or exceeds `usage_limit`.
3. **Idempotent Joins:** If a user is already a member of the conversation, joining via link succeeds idempotently without creating duplicate member entries.
