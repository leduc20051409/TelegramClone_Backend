# Complete Chat & Messaging API Specification

## Overview
This document specifies the REST and WebSocket STOMP API contracts for conversations, messaging, member management, invite links, reactions, and media handling.

---

## 1. WebSocket Protocol & Authentication

### Endpoint & Setup
- **URL Path:** `/ws`
- **Protocols Supported:** STOMP over WebSocket, SockJS fallback enabled on `/ws`
- **Header Authentication:** JWT token sent in STOMP `CONNECT` frame:
  ```text
  Authorization: Bearer <JWT_TOKEN>
  ```
- **Destination Prefixes:**
  - Client publish prefix: `/app`
  - User queue prefix: `/user`
  - Personal user event queue: `/user/queue/chat`
  - Public channel topic: `/topic/channels/{conversationId}`

---

## 2. WebSocket Event Envelope & Events

All WebSocket events pushed to `/user/queue/chat` (or channel topics) use the standardized `WsEnvelope<T>` envelope:

```json
{
  "event": "EVENT_TYPE",
  "timestamp": 1760000000000,
  "data": { ... }
}
```

### Supported Event Types:
- `NEW_MESSAGE`: Delivered when a new text/media message is sent.
- `MESSAGES_READ`: Delivered when a member marks messages as read.
- `TYPING`: Broadcast when a user starts or stops typing.
- `MESSAGE_EDITED`: Delivered when a message text is updated.
- `MESSAGE_DELETED`: Delivered when a message is soft-deleted.
- `MESSAGE_PINNED`: Delivered when a message is pinned in a conversation.
- `MESSAGE_UNPINNED`: Delivered when a message is unpinned.
- `CONVERSATION_UPDATED`: Delivered when group/channel title, description, or avatar changes.
- `COMMENT_COUNT_UPDATED`: Broadcast when a new comment is posted in a linked discussion thread.

---

## 3. WebSocket Destinations

### 3.1 Send Message
- **Destination:** `/app/chat.send`
- **Payload:**
  ```json
  {
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "message": "Hello world",
    "mediaIds": ["6e0d5749-7b0d-4f0a-9ac0-c6c4fc7a1001"],
    "replyToMessageId": 120
  }
  ```

### 3.2 Read Receipt
- **Destination:** `/app/chat.read`
- **Payload:**
  ```json
  {
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "lastReadMessageId": 125
  }
  ```

### 3.3 Typing Indicator
- **Destination:** `/app/chat.typing`
- **Payload:**
  ```json
  {
    "conversationId": "550e8400-e29b-41d4-a716-446655440000",
    "isTyping": true
  }
  ```

---

## 4. REST API - Conversation Management (`/api/conversations`)

### 4.1 Get or Create Private Chat
- **POST** `/api/conversations/private/{targetUserId}`
- **Response:** `200 OK` -> `ConversationResponse`

### 4.2 Create Group Chat
- **POST** `/api/conversations/group`
- **Request Body:**
  ```json
  {
    "title": "Development Team",
    "description": "Project discussion",
    "memberIds": ["user-uuid-1", "user-uuid-2"]
  }
  ```
- **Response:** `200 OK` -> `ConversationResponse`

### 4.3 Get User Conversations
- **GET** `/api/conversations`
- **Response:** `200 OK` -> `List<ConversationResponse>`

### 4.4 Update Conversation Info
- **PUT** `/api/conversations/{conversationId}`
- **Request Body:**
  ```json
  {
    "title": "New Title",
    "description": "Updated description",
    "avatarMediaId": "media-uuid"
  }
  ```
- **Response:** `200 OK` -> `ConversationResponse` (Broadcasts `CONVERSATION_UPDATED`)

### 4.5 Member & Role Management
- **POST** `/api/conversations/{conversationId}/members`: Add user to group.
- **POST** `/api/conversations/{conversationId}/join`: Join public conversation.
- **POST** `/api/conversations/{conversationId}/leave`: Leave conversation.
- **DELETE** `/api/conversations/{conversationId}/members/{userId}`: Remove member (Admin/Owner only).
- **PUT** `/api/conversations/{conversationId}/members/{userId}/role`: Change role (`MEMBER`, `ADMIN`, `OWNER`).
- **PUT** `/api/conversations/{conversationId}/members/me/mute?isMuted=true`: Mute/unmute notifications.

### 4.6 Message Pinning
- **POST** `/api/conversations/{conversationId}/pin/{messageId}`: Pin message (Broadcasts `MESSAGE_PINNED` & `NEW_MESSAGE` system notification).
- **DELETE** `/api/conversations/{conversationId}/unpin/{messageId}`: Unpin message (Broadcasts `MESSAGE_UNPINNED`).

### 4.7 Public Search
- **GET** `/api/conversations/search?query={keyword}`: Search public groups/channels.
- **GET** `/api/conversations/by-username/{username}`: Find public conversation by custom username.

---

## 5. REST API - Message Operations (`/api/messages`)

### 5.1 Get Chat History
- **GET** `/api/messages/{conversationId}?cursor={lastMessageId}&size=50`
- **Response:** `200 OK` -> `List<ChatMessageResponse>` (Cursor pagination by message ID).

### 5.2 Edit Message
- **PUT** `/api/messages/{messageId}`
- **Request Body:** `{"message": "New content"}`
- **Response:** `200 OK` -> `ChatMessageResponse` (Broadcasts `MESSAGE_EDITED`).

### 5.3 Delete Message
- **DELETE** `/api/messages/{messageId}`
- **Response:** `204 No Content` (Broadcasts `MESSAGE_DELETED`).

### 5.4 Search Messages
- **GET** `/api/messages/{conversationId}/search?query={text}&date={YYYY-MM-DD}`
- **Response:** `200 OK` -> `List<ChatMessageResponse>`

---

## 6. REST API - Reactions (`/api/messages/{messageId}/reactions`)

- **POST** `/api/messages/{messageId}/reactions?reaction=👍`: Add/toggle emoji reaction.
- **DELETE** `/api/messages/{messageId}/reactions`: Remove emoji reaction.

---

## 7. REST API - Invite Links (`/api/invite-links`)

- **POST** `/api/invite-links/generate`: Create dynamic invite link (with expiration date & usage limit).
- **GET** `/api/invite-links/{conversationId}`: List active invite links for conversation.
- **DELETE** `/api/invite-links/{linkId}`: Revoke an invite link.
- **GET** `/api/invite-links/info/{token}`: Preview conversation info before joining.
- **POST** `/api/invite-links/join/{token}`: Join conversation via link token.

---

## 8. REST API - Media Upload (`/api/media`)

- **POST** `/api/media/upload`: Upload file (`multipart/form-data`) -> returns `UploadResponseDto` with status `TEMP`.
- **DELETE** `/api/media/{mediaId}`: Delete uploaded temporary media.

---

## 9. REST API - Discussion Group (Channel ↔ Group Linking)

### 9.1 Link Discussion Group
- **POST** `/api/conversations/{channelId}/discussion/link`
- **Request Body:**
  ```json
  {
    "groupId": "550e8400-e29b-41d4-a716-446655440000"
  }
  ```
- **Response:** `200 OK` -> `DiscussionGroupInfoResponse`

### 9.2 Unlink Discussion Group
- **DELETE** `/api/conversations/{channelId}/discussion/unlink`
- **Response:** `204 No Content`

### 9.3 Get Linked Discussion Group Info
- **GET** `/api/conversations/{channelId}/discussion`
- **Response:** `200 OK` -> `DiscussionGroupInfoResponse`

### 9.4 Fetch Channel Post Discussion Thread & Comments
- **GET** `/api/messages/{channelPostId}/discussion-thread?cursor={lastCommentId}&size=50`
- **Response:** `200 OK` -> `DiscussionThreadResponse`
  ```json
  {
    "channelPostMessageId": 101,
    "channelId": "uuid-channel",
    "groupRootMessageId": 201,
    "groupId": "uuid-group",
    "commentCount": 5,
    "groupRootMessage": { ... },
    "comments": [ ... ]
  }
  ```
