# Chat API Documentation

## WebSocket Connection

### Endpoint
```
/ws
```

**Protocol:** STOMP (Simple Text Oriented Messaging Protocol)

**Authentication:** JWT Token required in the connection header (extracted from `Principal`)

---

## REST API - Conversation Management

### 1. Get or Create Private Conversation

#### Endpoint
```
POST /api/conversations/private/{targetUserId}
```

**Authentication:** Required (JWT Token)

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `targetUserId` | UUID | The ID of the user you want to chat with |

#### Request Headers
```
Authorization: Bearer <JWT_TOKEN>
```

#### Response (200 OK)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "PRIVATE",
  "title": null,
  "createdAt": "2026-03-06T10:30:45Z"
}
```

**Response Field Descriptions:**
- `id` (UUID): Unique conversation identifier
- `type` (string): Conversation type - "PRIVATE" or "GROUP"
- `title` (string, nullable): Conversation title (typically null for private chats)
- `createdAt` (ISO 8601 datetime): When the conversation was created

#### Behavior
- **GET semantics:** If a private conversation already exists between the current user and `targetUserId`, returns the existing conversation
- **CREATE semantics:** If no conversation exists, creates a new private conversation and adds both users as members
- **Idempotent:** Calling multiple times with the same `targetUserId` returns the same conversation

#### Error Responses
| Status | Error | Description |
|--------|-------|-------------|
| 400 | Bad Request | Invalid UUID format for `targetUserId` |
| 401 | Unauthorized | JWT token missing or invalid |
| 404 | Not Found | Target user does not exist |
| 500 | Internal Server Error | Database operation failed |

#### Example Usage
```bash
# Get or create private chat with user john@example.com (UUID: 660e8400-e29b-41d4-a716-446655440001)
curl -X POST http://localhost:8080/api/conversations/private/660e8400-e29b-41d4-a716-446655440001 \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

### 2. Get All Conversations for Current User

#### Endpoint
```
GET /api/conversations
```

**Authentication:** Required (JWT Token)

#### Response (200 OK)
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "type": "PRIVATE",
    "title": null,
    "createdAt": "2026-03-06T10:30:45Z"
  }
]
```

#### Error Responses
| Status | Error | Description |
|--------|-------|-------------|
| 401 | Unauthorized | JWT token missing or invalid |

---

## REST API - Message History

### Get Message History (Keyset Pagination)

#### Endpoint
```
GET /api/messages/{conversationId}?cursor={lastMessageId}&size={size}
```

**Authentication:** Required (JWT Token)

#### Query Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `cursor` | Long | The last message ID from the previous page (optional) |
| `size` | int | Number of messages to return (default: 50) |

#### Response (200 OK)
```json
[
  {
    "id": 120,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "770e8400-e29b-41d4-a716-446655440002",
    "message": "Hello",
    "createdAt": "2026-03-06T10:30:45Z"
  }
]
```

#### Behavior
- If `cursor` is omitted, returns the newest page (sorted by message ID descending).
- If `cursor` is provided, returns messages with `id < cursor` (keyset pagination).

#### Error Responses
| Status | Error | Description |
|--------|-------|-------------|
| 401 | Unauthorized | JWT token missing or invalid |
| 403 | Forbidden | User is not a member of the conversation |
| 404 | Not Found | Conversation does not exist |

---

## Message Sending

### Send Endpoint
```
/app/chat.send
```

### Request Payload
```json
{
  "conversationId": "uuid",
  "message": "string"
}
```

**Field Descriptions:**
- `conversationId` (UUID, **required**): The ID of the conversation/chat room
- `message` (string, **required**): The text content of the message

**Security Notes:**
- `senderId` is **NOT** accepted from the payload and is automatically extracted from the JWT token
- User must be a member of the conversation (validated against `conversation_members` table)
- Cannot send messages to a conversation if not a participant

---

## Message Receiving

### Subscribe Endpoint
```
/user/queue/chat
```

**Subscription Type:** Personal queue (user-specific)

---

### Response Envelope
All WebSocket messages are wrapped in a `WsEnvelope` structure:

```json
{
  "event": "NEW_MESSAGE",
  "timestamp": 1625097600000,
  "data": {
    "id": "uuid",
    "conversationId": "uuid",
    "senderId": "uuid",
    "message": "string",
    "createdAt": "2026-03-06T10:30:45Z"
  }
}
```

**Envelope Field Descriptions:**
- `event` (string): Type of WebSocket event (e.g., "NEW_MESSAGE")
- `timestamp` (number): Server timestamp in milliseconds (Unix epoch)
- `data` (object): The actual message payload

**Message Data Field Descriptions:**
- `id` (UUID): Unique message identifier
- `conversationId` (UUID): The conversation this message belongs to
- `senderId` (UUID): The ID of the sender (extracted from JWT token)
- `message` (string): The message content
- `createdAt` (ISO 8601 datetime): When the message was created

---

## Example Flow

### 1. Establish WebSocket Connection
```bash
# Client connects to /ws with JWT token
ws://localhost:8080/ws
Header: Authorization: Bearer <JWT_TOKEN>
```

### 2. Send a Message
```
Destination: /app/chat.send
Payload:
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Hello, how are you?"
}
```

### 3. Receive Messages
```
Subscribe to: /user/queue/chat
Response received:
{
  "event": "NEW_MESSAGE",
  "timestamp": 1625097600000,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "770e8400-e29b-41d4-a716-446655440002",
    "message": "Hello, how are you?",
    "createdAt": "2026-03-06T10:30:45Z"
  }
}
```

---

## Read Receipts

### Send Read Receipt
```
/app/chat.read
```

### Request Payload
```json
{
  "conversationId": "uuid",
  "lastReadMessageId": 120
}
```

**Field Descriptions:**
- `conversationId` (UUID, **required**): The ID of the conversation
- `lastReadMessageId` (Long, **required**): The last message ID the user has read

### Read Receipt Event
```json
{
  "event": "MESSAGES_READ",
  "timestamp": 1625097600000,
  "data": {
    "conversationId": "uuid",
    "lastReadMessageId": 120
  }
}
```

**Notes:**
- Read receipts are broadcast to other members only (not echoed to the reader).
- User must be a member of the conversation.

---

## Error Handling

### Possible Error Responses
Messages may fail to send due to:

| Error | Cause | HTTP Status / WebSocket Response |
|-------|-------|----------------------------------|
| User not a conversation member | Attempting to send message to a chat room they don't belong to | 403 Forbidden / WebSocket ERROR event |
| Invalid conversation ID | Conversation does not exist | 404 Not Found / WebSocket ERROR event |
| Empty message | Message payload is empty | 400 Bad Request |
| Unauthorized | JWT token missing or invalid | 401 Unauthorized |

---

## Transactional Guarantee

- All message operations are wrapped in `@Transactional`
- Message is **first persisted** to the database
- **Only after** successful database commit, the message is broadcast via WebSocket
- This ensures no message loss and maintains data consistency

---

## Future Enhancements

- [ ] Retrieve conversation history with Keyset Pagination
- [ ] Support for group chat creation and management
- [ ] Message editing and deletion
- [ ] Message reactions and replies
- [ ] Typing indicators (real-time "user is typing" notifications)
- [ ] Read receipts
