# Messaging Module

## Responsibilities
- Manage 1:1 Private chats, Group chats (`GROUP`), and Announcement Channels (`CHANNEL`).
- Receive, validate, and store text messages and media attachments in PostgreSQL.
- Real-time message delivery to conversation members via WebSocket + STOMP.
- Keyset / Cursor-based chat history pagination (by message ID).
- Unread message tracking (`unread_counters` table) and real-time read receipt delivery (`MESSAGES_READ`).
- Message Editing (`editMessage`) and Soft Deletion (`deleteMessage`).
- Message Pinning (`pinMessage`, `unpinMessage`) with automated System notification messages.
- Message Emoji Reactions (`MessageReactionService`).
- Message Search within conversations (by text query or date filter).
- Channel Announcement View Counter tracking (`MessagePostView`).

## Rules & Security Standards

### 1. Conversation Types & Uniqueness
- **Private Chats (`PRIVATE`):** Unique 1:1 chat room per user pair using Find-or-Create logic to prevent duplicate rooms.
- **Group Chats (`GROUP`):** Multi-user conversation managed by admins and members.
- **Channels (`CHANNEL`):** Broadcast conversations where messages can be sent by Admins/Owners and broadcasted to channel subscribers via STOMP topic (`/topic/channels/{id}`) when member count exceeds threshold (>1000).

### 2. Message Sending Security
- A user cannot create a private chat with themselves.
- Sender identity (`senderId`) must **never** be accepted from client payloads. It is strictly extracted from the WebSocket `Principal` (JWT Token).
- The user **must** be an active member of the conversation (`conversation_members` table).

### 3. Read Receipts & Unread Tracking
- A user can only mark messages as read in conversations where they are a member.
- Read receipts update `unread_counters` in PostgreSQL and broadcast `MESSAGES_READ` to other conversation members.

### 4. Message Editing & Deletion
- **Editing:** Only the original sender can edit a message's text body within permitted constraints. Sets `edited = true` and broadcasts `MESSAGE_EDITED`.
- **Deletion:** Sender or conversation Admin/Owner can soft-delete a message (`deleted = true`). Broadcasts `MESSAGE_DELETED`.

### 5. Pinned Messages & Reactions
- **Pinning:** Admins/Members can pin messages. Pinning generates a `SYSTEM` type message notifying members and broadcasts `MESSAGE_PINNED`.
- **Reactions:** Members can add or remove emoji reactions (`MessageReaction`). Real-time reaction updates are sent to members.

### 6. Channel View Counters
- Unique views for channel posts are recorded in `MessagePostView` (linking `userId`, `conversationId`, and `messageId`) to prevent duplicate view increments.

### 7. Transactional Consistency & WebSocket Envelopes
- All message operations (saving, media binding, pinning) are wrapped in `@Transactional`.
- All real-time WebSocket events are standardized using `WsEnvelope<T>` (`event`, `timestamp`, `data`).

### 8. Discussion Group (Channel ↔ Group Linking)
- **1-to-1 Linking:** A Channel (`CHANNEL`) can be linked with at most one Group (`GROUP`). A Group can be linked with at most one Channel. Managed by Channel/Group Admins/Owners.
- **Auto-Forwarding:** New posts on the Channel are automatically copied into the linked Group as a thread root (`forwarded_from_conversation_id`, `forwarded_from_user_id`), creating a `DiscussionThreadLink` mapping record.
- **Comment Count & Real-time Sync:** Replies to a thread root in the Group increment `comment_count` on `DiscussionThreadLink` atomically and broadcast `COMMENT_COUNT_UPDATED` WS events to both the Channel topic and Group members.
- **Telegram Edge Case Compliance:**
  - Deleting a channel post leaves the discussion thread in the group intact.
  - Editing a channel post does not overwrite or re-forward to the group thread.
  - Unlinking or re-linking a group leaves existing discussion threads in their original groups.
