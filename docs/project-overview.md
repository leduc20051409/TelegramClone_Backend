# Project Overview

## Tech Stack
- **Framework & Language:** Spring Boot 3.5+, Java 21
- **Database:** PostgreSQL 16
- **Cache & Real-time Store:** Redis 7 (Caching, Atomic Rate Limiting / Slow Mode, Presence & Heartbeats)
- **Security & Auth:** Spring Security, JWT (`jjwt` library), BCrypt
- **Real-time Gateway:** WebSocket + STOMP, SockJS
- **Storage:** Cloudinary (Media storage & image hosting)
- **Email:** Spring Mail (Password reset OTP)
- **Object Mapping:** MapStruct & Lombok

---

## Project Structure & Architecture
Hexagonal & Layered Monolith Architecture (`com.leanhduc.telegramclone`):

```text
com.leanhduc.telegramclone
│
├── config/                 # System Configurations (WebSocket STOMP, Security, CORS, Cloudinary, Scheduling, Beans)
│
├── controller/             # Request Handling Layer (REST & WebSocket Endpoints)
│   ├── websocket/
│   │   ├── ChatController.java               # Thin Controller: Delegates STOMP messages (/app/chat.send, /app/chat.read) to MessageService; Direct broadcast for ephemeral typing (/app/chat.typing)
│   │   └── PresenceController.java           # Real-time user online/offline presence broadcasts
│   │
│   ├── AuthController.java                   # Authentication, registration, token refresh, forgot/reset password
│   ├── ContactController.java                # Contact list management, alias, mute & block status
│   ├── ConversationController.java           # 1:1 chat, Group, Channel management, member operations, Slow Mode configuration, pinning & post views
│   ├── ConversationInviteLinkController.java # Dynamic group/channel invite links (generate, info preview, join, revoke)
│   ├── MediaController.java                  # File & media upload/delete pipeline (Cloudinary)
│   ├── MessageController.java                # Chat history, edit/delete message, message forwarding, search messages
│   ├── MessageReactionController.java        # Emoji reactions on messages (add/remove)
│   └── UserController.java                   # User profile management, avatar updates, global user search
│
├── dto/                    # Data Transfer Objects (Java Records & Lombok DTOs)
│   ├── auth/               # Login, Register, RefreshToken, PasswordReset DTOs
│   ├── contact/            # Contact requests and responses
│   ├── conversation/       # Group/Channel creation, update, Slow Mode, permissions, member role DTOs
│   ├── email/              # Email context payloads
│   ├── invite/             # Invite link creation, preview, and join DTOs
│   ├── message/            # Chat message payloads, read receipts, typing, reaction, discussion DTOs
│   ├── user/               # User profile, update request, summary DTOs
│   └── websocket/          # WsEnvelope wrapper & event payload DTOs
│
├── event/                  # Domain Events (Decoupled lifecycle & transport)
│   ├── DiscussionBroadcastEvent.java         # Group root message & comment count updates
│   ├── MessageCreatedEvent.java              # Fired after new message is persisted (ChatMessageResponse, ConversationType)
│   ├── MessageDeletedEvent.java              # Fired after message soft delete
│   ├── MessageEditedEvent.java               # Fired after message content edit
│   ├── MessagesForwardedEvent.java           # Fired after batch message forwarding
│   ├── MessagesReadEvent.java                # Fired after read receipt is persisted (conversationId, type, readerId, lastReadMessageId)
│   └── UserPresenceChangedEvent.java         # Fired on online/offline state change
│
├── exception/              # Global Exception Handling (@ControllerAdvice, BusinessException, ErrorCode)
│
├── listener/               # Transactional Event Listeners (@TransactionalEventListener AFTER_COMMIT)
│   ├── DiscussionBroadcastListener.java      # Broadcasts channel discussion mirroring & comment count updates
│   ├── MessageBroadcastListener.java         # Broadcasts NEW_MESSAGE, MESSAGES_READ, MESSAGE_EDITED, MESSAGE_DELETED via ChatBroadcaster
│   ├── MessageForwardBroadcastListener.java  # Broadcasts forwarded messages via ChatBroadcaster
│   ├── PresenceEventListener.java            # Broadcasts user presence updates
│   └── WebSocketEventListener.java           # Handles STOMP connect, disconnect, and session subscription lifecycle
│
├── mapper/                 # MapStruct & Manual Mappers (Entity ↔ DTO conversion)
│   ├── ContactMapper.java
│   ├── ConversationInviteLinkMapper.java
│   ├── ConversationMapper.java
│   ├── MediaMapper.java
│   ├── MessageMapper.java
│   ├── RefreshTokenMapper.java
│   └── UserMapper.java
│
├── model/                  # JPA Entities (PostgreSQL Database Schema)
│   ├── Contact.java, ContactId.java
│   ├── Conversation.java                     # Supports type, slowModeDelaySeconds, linkedDiscussionGroupId, permissions
│   ├── ConversationInviteLink.java
│   ├── ConversationMember.java, ConversationMemberId.java # Role, joinedAt, leftAt, member/admin permissions
│   ├── DiscussionThreadLink.java             # Maps channel post to discussion group root message with comment counter
│   ├── Media.java
│   ├── Message.java                          # Soft delete, replyTo, forwardedFrom tracking, media associations
│   ├── MessageMedia.java, MessageMediaId.java
│   ├── MessagePostView.java
│   ├── MessageReaction.java, MessageReactionId.java
│   ├── PasswordResetToken.java
│   ├── PinnedMessage.java, PinnedMessageId.java
│   ├── RefreshToken.java
│   ├── UnreadCounter.java, UnreadCounterId.java
│   ├── User.java
│   └── enums/              # ConversationType, ConversationRole, MessageType, RoleUser, MediaStatus, AdminPermission, MemberPermission
│
├── repository/             # Data Access Layer (Spring Data JPA Repositories)
│   ├── ContactRepository.java
│   ├── ConversationInviteLinkRepository.java
│   ├── ConversationMemberRepository.java     # Added countByConversationIdAndLeftAtIsNull for high-performance threshold checks
│   ├── ConversationRepository.java
│   ├── DiscussionThreadLinkRepository.java
│   ├── MediaRepository.java
│   ├── MessageMediaRepository.java
│   ├── MessagePostViewRepository.java
│   ├── MessageReactionRepository.java
│   ├── MessageRepository.java
│   ├── PasswordResetTokenRepository.java
│   ├── PinnedMessageRepository.java
│   ├── RefreshTokenRepository.java
│   ├── UnreadCounterRepository.java
│   └── UserRepository.java
│
├── security/               # Security Configuration, JWT Authentication Filter, Custom UserDetailsService
│
├── service/                # Business Logic Layer
│   ├── Auth/               # Registration, Login, Password Reset logic (AuthService)
│   ├── broadcast/          # Centralized WebSocket Broadcast Abstraction
│   │   ├── ChatBroadcaster.java              # broadcast(convId, type, envelope), broadcastExcept(...)
│   │   └── StompChatBroadcaster.java         # High-perf routing: Topic for Channel > 1000 vs User Queue for members
│   ├── contact/            # Contact management logic (ContactService)
│   ├── conversation/       # Conversation, Group, Channel, Slow Mode logic (ConversationService, PermissionService)
│   ├── email/              # Async Email dispatch logic (EmailService)
│   ├── invite/             # Dynamic invite links logic (ConversationInviteLinkService)
│   ├── media/              # Media storage pipeline & Cloudinary integration (MediaService)
│   ├── message/            # Messaging, reactions, pinning, search logic (MessageService, MessageReactionService)
│   │   └── discussion/     # Channel discussion group linking & comment thread syncing (DiscussionService)
│   ├── Presence/           # Redis presence & last seen tracking (PresenceService)
│   ├── RefreshToken/       # Refresh token rotation & revocation logic (RefreshTokenService)
│   ├── typing/             # Real-time typing status tracking (TypingService)
│   └── user/               # Profile & user search logic (UserService)
│
└── utils/                  # Helper utilities (JwtUtils, etc.)
```

---

## Core Architectural Highlights & Mechanisms

### 1. Unified Event-Driven Real-Time Architecture
- **Standardized Lifecycle:**
  $$\text{Client Command} \longrightarrow \text{Service DB Persistence} \longrightarrow \text{Domain Event} \xrightarrow{\text{AFTER\_COMMIT}} \text{Listener} \longrightarrow \text{ChatBroadcaster} \longrightarrow \text{STOMP Broadcast}$$
- **Strict Transaction Boundary:** Listeners use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with `fallbackExecution = false`. Broadcasts **never** execute if the database transaction rolls back or fails.
- **Zero-Duplication Routing (`ChatBroadcaster`):**
  - **Large Channels (`> 1000` members):** Checks `memberRepository.countByConversationIdAndLeftAtIsNull(...)` with a fast SQL `COUNT(*)`. If count exceeds 1000, sends to `/topic/channels/{conversationId}` without loading member IDs into heap memory.
  - **Groups, 1:1 Chats, and Small Channels:** Routes to `/queue/chat` per active member.
  - **Selective Exclusion (`broadcastExcept`):** Automatically excludes the trigger user (e.g., skips the reader on `MESSAGES_READ` notifications).
- **Thin Controller:** `ChatController` delegates commands to `MessageService`. Only ephemeral, non-persisted events (`/chat.typing`) broadcast directly.

### 2. Group Slow Mode Enforcement
- **Atomic Rate Limiting:** Enforces cooldowns using Redis `SET slowmode:{convId}:{userId} 1 NX EX {seconds}` to eliminate race conditions under concurrent requests.
- **Compensation Cleanup:** If database persistence fails or multi-destination forwarding encounters an error, acquired Redis keys are automatically deleted (`redisTemplate.delete(key)`) to prevent penalizing users unfairly.
- **Role Bypass:** Group Owner and Admins bypass Slow Mode restrictions.

### 3. Channel Discussion Groups
- Channels can be linked to a discussion group (`linkedDiscussionGroupId`).
- When a channel post is created, it is automatically mirrored as a group root message via `DiscussionThreadLink`.
- Replies to the mirrored post in the group update comment counters on the original channel post and broadcast `COMMENT_COUNT_UPDATED` in real time.

### 4. Granular Permissions System
- `MemberPermission`: Controls member capabilities (`SEND_MESSAGES`, `SEND_MEDIA`, `ADD_USERS`, `PIN_MESSAGES`, etc.).
- `AdminPermission`: Controls administrative capabilities (`CHANGE_INFO`, `POST_MESSAGES`, `EDIT_MESSAGES`, `DELETE_MESSAGES`, `BAN_USERS`, `INVITE_USERS`, `MANAGE_TOPICS`).
- Enforced via `PermissionService` before mutations take place.

---

## Global Design Principles & Patterns
- **Layered Monolith:** Strict hierarchy: `Controller` → `Service` → `Repository` → `Database`. Direct database calls from controllers or cyclic service dependencies are strictly prohibited.
- **Primary Keys:** UUID for entities, `BIGINT` identity for messages and counters (optimized for cursor pagination and B-Tree indexing).
- **Error Handling:** Standardized error codes via `ErrorCode` enum and unified error response structure handled by `GlobalExceptionHandler`.
- **DTO Immutability:** Java records and Lombok DTOs used exclusively for API communication.
- **Catch-up Sync:** Clients reconcile missed messages using cursor-based pagination after connection interruptions.
