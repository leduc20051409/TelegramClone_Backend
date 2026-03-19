# Messaging Module

## Responsibilities
- Initialize or retrieve a private chat room (1–1) between two users (`getOrCreatePrivateConversation`).
- Receive, validate, and store text messages in the database (PostgreSQL).
- Broadcast messages in real time to the correct recipient via WebSocket + STOMP.
- Wrap real-time data using the common format (`WsEnvelope`).
- Retrieve conversation history with keyset pagination (cursor by message ID).
- Mark messages as read and update unread tracking (`unread_counters`).
- Broadcast read receipts to other members (`MESSAGES_READ`).
- (Future) Support creating and managing group chats.

## Rules
- **Uniqueness:** Between two users, there must be only one `PRIVATE` chat room. Use the "Find-or-Create" logic to avoid duplicates.

- **Message sending security:**
    - A user cannot create a chat room with themselves.
    - A user is **only allowed** to send messages to chat rooms where they are a member (validated through the `conversation_members` table).
    - The `senderId` must **never** be taken from the client payload (to prevent spoofing). Instead, it must be extracted from the `Principal` (JWT Token) of the STOMP connection.

- **Read receipts security:**
    - A user is **only allowed** to mark messages as read in conversations where they are a member.
    - Read receipts are broadcast to other members, not echoed back to the reader.

- **Synchronization:** Every message sending action must be wrapped in `@Transactional` to ensure that messages are sent through STOMP only after they are successfully stored in the database.

- **Payload Standardization:** All messages pushed through WebSocket must be wrapped in `WsEnvelope` (containing `event`, `timestamp`, and `data`).
