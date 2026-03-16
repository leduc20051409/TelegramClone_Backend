# Messaging Module

## Responsibilities
- Initialize or retrieve a private chat room (1–1) between two users (`getOrCreatePrivateConversation`).
- Receive, validate, and store text messages in the database (PostgreSQL).
- Broadcast messages in real time to the correct recipient via WebSocket + STOMP.
- Wrap real-time data using the common format (`WsEnvelope`).
- (Future) Retrieve conversation history (Keyset Pagination).
- (Future) Support creating and managing group chats.

## Rules
- **Uniqueness:** Between two users, there must be only one `PRIVATE` chat room. Use the "Find-or-Create" logic to avoid duplicates.

- **Message sending security:**
    - A user cannot create a chat room with themselves.
    - A user is **only allowed** to send messages to chat rooms where they are a member (validated through the `conversation_members` table).
    - The `senderId` must **never** be taken from the client payload (to prevent spoofing). Instead, it must be extracted from the `Principal` (JWT Token) of the STOMP connection.

- **Synchronization:** Every message sending action must be wrapped in `@Transactional` to ensure that messages are sent through STOMP only after they are successfully stored in the database.

- **Payload Standardization:** All messages pushed through WebSocket must be wrapped in `WsEnvelope` (containing `event`, `timestamp`, and `data`).