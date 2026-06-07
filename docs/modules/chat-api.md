# Chat API Documentation

## Scope

This document describes the currently implemented chat-related REST and WebSocket APIs.

- Conversation management
- Message history
- Real-time message delivery
- Read receipts
- Media upload flow used by chat messages

---

## WebSocket Connection

### Endpoint
```text
/ws
```

### Protocol
- STOMP over WebSocket
- SockJS endpoint is enabled on the same `/ws` path

### Authentication
- JWT is required for authenticated chat operations
- The client sends the token in the STOMP `CONNECT` frame header:

```text
Authorization: Bearer <JWT_TOKEN>
```

- The server extracts the authenticated user from the JWT and stores it in `Principal`
- `senderId` is never accepted from the client payload

### Destination Prefixes
- App destination prefix: `/app`
- User destination prefix: `/user`
- Personal queue used by chat events: `/user/queue/chat`

---

## REST API - Conversation Management

### 1. Get or Create Private Conversation

#### Endpoint
```text
POST /api/conversations/private/{targetUserId}
```

**Authentication:** Required

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `targetUserId` | UUID | Target user ID |

#### Request Headers
```text
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

#### Behavior
- If a private conversation already exists between the current user and `targetUserId`, the existing conversation is returned
- Otherwise a new private conversation is created and both users are added as members
- Repeated calls with the same pair of users are effectively idempotent

#### Error Responses
| Status | Error | Description |
|--------|-------|-------------|
| 400 | Bad Request | Invalid UUID format or attempting to chat with yourself |
| 401 | Unauthorized | JWT missing or invalid |
| 404 | USER_NOT_FOUND | Target user does not exist |

---

### 2. Get All Conversations for Current User

#### Endpoint
```text
GET /api/conversations
```

**Authentication:** Required

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
| 401 | Unauthorized | JWT missing or invalid |

---

## REST API - Message History

### Get Message History

#### Endpoint
```text
GET /api/messages/{conversationId}?cursor={lastMessageId}&size={size}
```

**Authentication:** Required

#### Query Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `cursor` | Long | Last message ID from the previous page, optional |
| `size` | int | Page size, default `50` |

#### Response (200 OK)
```json
[
  {
    "id": 120,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "770e8400-e29b-41d4-a716-446655440002",
    "message": "Hello",
    "createdAt": "2026-03-06T10:30:45Z",
    "media": [
      {
        "id": "6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001",
        "url": "https://storage.example.com/chat/file.png",
        "mimeType": "image/png",
        "fileName": "file.png",
        "fileSize": 24567
      }
    ]
  }
]
```

#### Behavior
- If `cursor` is omitted, the newest page is returned
- If `cursor` is provided, messages with `id < cursor` are returned
- The current implementation uses message ID based pagination
- The response may include media attachments per message

#### Error Responses
| Status | Error | Description |
|--------|-------|-------------|
| 401 | Unauthorized | JWT missing or invalid |
| 403 | NOT_IN_CONVERSATION | User is not a member of the conversation |

Note:
- The service checks membership before loading history
- The current implementation does not explicitly return `404` for a non-member requesting a missing conversation; membership validation is the primary guard

---

## REST API - Media Upload For Chat Messages

Upload media first, then reference returned media IDs in the chat send payload.

### Upload Temporary Media

#### Endpoint
```text
POST /api/media/upload
```

**Authentication:** Required

**Content-Type:** `multipart/form-data`

#### Request
- Form field: `file`

#### Response
- Returns `UploadResponseDto`
- Use the returned media ID in `mediaIds` when sending a chat message

### Delete Temporary Media

#### Endpoint
```text
DELETE /api/media/{mediaId}
```

**Authentication:** Required

#### Behavior
- Deletes a temporary uploaded file owned by the current user

#### Chat Media Rules
- `mediaIds` are optional in chat messages
- All referenced media must belong to the sender
- Duplicate media IDs are rejected
- Only temporary media can be attached to a message
- Attached media becomes active after a successful send

---

## WebSocket - Message Sending

### Send Message

#### Destination
```text
/app/chat.send
```

#### Request Payload
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Hello, how are you?",
  "mediaIds": [
    "6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001"
  ]
}
```

#### Field Descriptions
- `conversationId` (UUID, required): Conversation ID
- `message` (string, optional by current implementation): Text body
- `mediaIds` (UUID array, optional): Uploaded media IDs to attach

#### Notes
- A text-only message may omit `mediaIds`
- A file message uses one or more `mediaIds`
- The current implementation does not enforce `@NotBlank` validation on `message`
- The sender is derived from `Principal`
- The sender must be a member of the conversation

---

## WebSocket - Message Receiving

### Subscribe Destination
```text
/user/queue/chat
```

**Subscription Type:** Personal user queue

### Event Envelope
All chat WebSocket events use this envelope:

```json
{
  "event": "NEW_MESSAGE",
  "timestamp": 1760000000000,
  "data": {}
}
```

#### Envelope Fields
- `event` (string): Event type
- `timestamp` (number): Server time in Unix epoch milliseconds
- `data` (object): Event payload

### `NEW_MESSAGE` Event

```json
{
  "event": "NEW_MESSAGE",
  "timestamp": 1760000000000,
  "data": {
    "id": 121,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "770e8400-e29b-41d4-a716-446655440002",
    "message": "Hello, how are you?",
    "createdAt": "2026-03-06T10:30:45Z",
    "media": [
      {
        "id": "6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001",
        "url": "https://storage.example.com/chat/file.png",
        "mimeType": "image/png",
        "fileName": "file.png",
        "fileSize": 24567
      }
    ]
  }
}
```

#### `NEW_MESSAGE` Payload Fields
- `id` (Long): Message ID
- `conversationId` (UUID): Conversation ID
- `senderId` (UUID): Sender user ID
- `message` (string): Message body
- `createdAt` (ISO 8601 datetime): Creation time
- `media` (array): Attached media metadata

#### Delivery Behavior
- The server sends the event to each conversation member on `/user/queue/chat`
- The sender also receives the event

---

## WebSocket - Read Receipts

### Send Read Receipt

#### Destination
```text
/app/chat.read
```

#### Request Payload
```json
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "lastReadMessageId": 120
}
```

#### Field Descriptions
- `conversationId` (UUID, required): Conversation ID
- `lastReadMessageId` (Long, required): Highest message ID read by the current user

### `MESSAGES_READ` Event

```json
{
  "event": "MESSAGES_READ",
  "timestamp": 1760000000000,
  "data": {
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "lastReadMessageId": 120
  }
}
```

#### Delivery Behavior
- Read receipt events are sent to other conversation members only
- The reader does not receive their own `MESSAGES_READ` event
- The user must be a member of the conversation

---

## Error Handling

### REST Errors
REST endpoints use the global exception response shape:

```json
{
  "timestamp": "2026-04-12T10:15:30.123",
  "status": 403,
  "error": "NOT_IN_CONVERSATION",
  "message": "You are not a member of this conversation"
}
```

Common chat-related business errors:

| Status | Error | Meaning |
|--------|-------|---------|
| 400 | CANNOT_CHAT_WITH_YOURSELF | User tried to create a private conversation with self |
| 400 | DUPLICATE_MEDIA | Repeated media ID in one message |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | NOT_IN_CONVERSATION | User is not a member of the conversation |
| 403 | MEDIA_NOT_ACCESSIBLE | Media does not belong to sender or is not attachable |
| 404 | USER_NOT_FOUND | Target user does not exist |
| 404 | CONVERSATION_NOT_FOUND | Conversation does not exist during message send |

### WebSocket Errors
- Chat operations can fail with the same business rules as REST
- The current codebase does not define a documented custom WebSocket error envelope in this module
- Do not assume a stable `ERROR` event payload contract until it is implemented explicitly

---

## Transaction Notes

- `saveMessage` is transactional in the service layer
- Message persistence and media attachment persistence occur in the same transactional flow
- `getMessageHistory` is transactional read-only
- `markMessagesAsRead` is implemented, but this document does not claim an explicit transactional guarantee for it
- This document also does not claim post-commit-only WebSocket broadcasting because that behavior is not explicitly enforced by the current controller/service structure

---

## Example Flow

### 1. Upload Media
```bash
curl -X POST http://localhost:8080/api/media/upload \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@image.png"
```

### 2. Connect To WebSocket
```text
Endpoint: /ws
STOMP CONNECT header:
Authorization: Bearer <JWT_TOKEN>
```

### 3. Send Message
```text
Destination: /app/chat.send
Payload:
{
  "conversationId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "See attachment",
  "mediaIds": ["6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001"]
}
```

### 4. Receive Event
```text
Subscribe: /user/queue/chat
```

```json
{
  "event": "NEW_MESSAGE",
  "timestamp": 1760000000000,
  "data": {
    "id": 121,
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "senderId": "770e8400-e29b-41d4-a716-446655440002",
    "message": "See attachment",
    "createdAt": "2026-03-06T10:30:45Z",
    "media": [
      {
        "id": "6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001",
        "url": "https://storage.example.com/chat/file.png",
        "mimeType": "image/png",
        "fileName": "file.png",
        "fileSize": 24567
      }
    ]
  }
}
```

---

## Not Yet Documented Here

The following features are not documented as stable chat API contracts in this file:

- Group chat creation and management
- Message editing and deletion
- Message reactions and replies
- Typing indicators
- A custom WebSocket error contract
