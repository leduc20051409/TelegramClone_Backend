# Project Overview

## Tech Stack
- **Framework & Language:** Spring Boot 3.5+, Java 21
- **Database:** PostgreSQL 16
- **Cache & Real-time Store:** Redis 7
- **Security & Auth:** Spring Security, JWT (jjwt library), BCrypt
- **Real-time Gateway:** WebSocket + STOMP, SockJS
- **Storage:** Cloudinary (Media storage)
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
│   │   ├── ChatController.java               # Real-time WebSocket messaging (/app/chat.send, /app/chat.read, /app/chat.typing)
│   │   └── PresenceController.java           # Real-time user online/offline presence broadcasts
│   │
│   ├── AuthController.java                   # Authentication, registration, token refresh, forgot/reset password
│   ├── ContactController.java                # Contact list management, alias, mute & block status
│   ├── ConversationController.java           # 1:1 chat, Group, Channel management, member operations, pinning & post views
│   ├── ConversationInviteLinkController.java # Dynamic group/channel invite links (generate, info preview, join, revoke)
│   ├── MediaController.java                  # File & media upload/delete pipeline (Cloudinary)
│   ├── MessageController.java                # Chat history, edit/delete message, search messages
│   ├── MessageReactionController.java        # Emoji reactions on messages (add/remove)
│   └── UserController.java                   # User profile management, avatar updates, global user search
│
├── dto/                    # Data Transfer Objects
│   ├── auth/               # Login, Register, RefreshToken, PasswordReset DTOs
│   ├── contact/            # Contact requests and responses
│   ├── conversation/       # Group/Channel creation, update, member role DTOs
│   ├── email/              # Email context payloads
│   ├── invite/             # Invite link creation, preview, and join DTOs
│   ├── message/            # Chat message payloads, read receipts, typing, search DTOs
│   ├── user/               # User profile, update request, summary DTOs
│   └── websocket/          # WsEnvelope wrapper & event payload DTOs
│
├── exception/              # Global Exception Handling (@ControllerAdvice, custom exceptions)
│
├── listener/               # Event Listeners (Application events, STOMP session lifecycle events)
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
│   ├── Conversation.java
│   ├── ConversationInviteLink.java
│   ├── ConversationMember.java, ConversationMemberId.java
│   ├── Media.java
│   ├── Message.java
│   ├── MessageMedia.java, MessageMediaId.java
│   ├── MessagePostView.java
│   ├── MessageReaction.java, MessageReactionId.java
│   ├── PasswordResetToken.java
│   ├── PinnedMessage.java, PinnedMessageId.java
│   ├── RefreshToken.java
│   ├── UnreadCounter.java, UnreadCounterId.java
│   ├── User.java
│   └── enums/              # ConversationType, ConversationRole, MessageType, RoleUser, MediaStatus
│
├── repository/             # Data Access Layer (Spring Data JPA Repositories)
│   ├── ContactRepository.java
│   ├── ConversationInviteLinkRepository.java
│   ├── ConversationMemberRepository.java
│   ├── ConversationRepository.java
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
│   ├── contact/            # Contact management logic (ContactService)
│   ├── conversation/       # Conversation, Group, Channel logic (ConversationService)
│   ├── email/              # Async Email dispatch logic (EmailService)
│   ├── invite/             # Dynamic invite links logic (ConversationInviteLinkService)
│   ├── media/              # Media storage pipeline & Cloudinary integration (MediaService)
│   ├── message/            # Messaging, reactions, pinning, search logic (MessageService, MessageReactionService)
│   ├── Presence/           # Redis presence & last seen tracking (PresenceService)
│   ├── RefreshToken/       # Refresh token rotation & revocation logic (RefreshTokenService)
│   ├── typing/             # Real-time typing status tracking (TypingService)
│   └── user/               # Profile & user search logic (UserService)
│
└── utils/                  # Helper utilities (JwtUtils, etc.)
```

---

## Global Design Principles & Patterns
- **Layered Architecture:** Strict hierarchy: `Controller` → `Service` → `Repository` → `Database`. Direct database calls from controllers or cyclic service dependencies are prohibited.
- **Primary Keys:** UUID used for all entity primary keys except `messages` (`BIGINT` identity) and `ordinal` counters for performance.
- **Error Handling:** Centralized global exception handler (`GlobalExceptionHandler`) returning standardized JSON error payloads.
- **DTO Immutability:** Java records and Lombok DTOs used exclusively for API communication.
