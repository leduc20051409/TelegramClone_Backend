# Project Overview

## Tech Stack
- Spring Boot 3.5.7
- Java 21
- PostgreSQL
- Redis
- Spring Security + JWT
- MapStruct

## Architecture
- Monolith
- Layered architecture:
```
  com.leandhuc.telegramclone
  │
  ├── config/                 # Cấu hình hệ thống (WebSocket, CORS, Bean, etc.)
  │
  ├── controller/             # Tầng nhận request (REST + WebSocket)
  │   ├── websocket/
  │   │   ├── ChatController.java       # WebSocket chat send/read
  │   │   └── PresenceController.java   # Xử lý realtime presence (online/offline)
  │   │
  │   ├── AuthController.java           # API đăng nhập / đăng ký / refresh token
  │   ├── ContactController.java        # API quản lý danh bạ
  │   ├── ConversationController.java   # API quản lý cuộc hội thoại
  │   ├── MessageController.java        # API lấy lịch sử tin nhắn
  │   └── UserController.java           # API quản lý thông tin user
  │
  ├── dto/                    # Data Transfer Object (Request / Response)
  │   ├── auth/               # DTO cho authentication
  │   ├── contact/            # DTO cho contact
  │   ├── conversation/       # DTO cho conversation
  │   ├── message/            # DTO cho message
  │   ├── user/
  │   │   ├── UpdateProfileRequest.java
  │   │   ├── UserDto.java
  │   │   └── UserSummaryDto.java
  │   └── websocket/          # DTO cho WebSocket events
  │
  ├── exception/              # Xử lý exception toàn cục (GlobalExceptionHandler, custom exception)
  │
  ├── listener/               # Event listener (ApplicationEvent, WebSocket event, etc.)
  │
  ├── mapper/                 # MapStruct / manual mapper (Entity ↔ DTO)
  │   ├── ContactMapper.java
  │   ├── ConversationMapper.java
  │   ├── MessageMapper.java
  │   ├── RefreshTokenMapper.java
  │   └── UserMapper.java
  │
  ├── model/                  # Entity (JPA entities mapping database tables)
  │   ├── Contact.java
  │   ├── ContactId.java
  │   ├── Conversation.java
  │   ├── ConversationMember.java
  │   ├── ConversationMemberId.java
  │   ├── Message.java
  │   ├── UnreadCounter.java
  │   ├── UnreadCounterId.java
  │   ├── RefreshToken.java
  │   ├── User.java
  │   └── enums/              # Enum types
  │
  ├── repository/             # Data Access Layer (Spring Data JPA)
  │   ├── ContactRepository.java
  │   ├── ConversationRepository.java
  │   ├── ConversationMemberRepository.java
  │   ├── MessageRepository.java
  │   ├── UnreadCounterRepository.java
  │   ├── RefreshTokenRepository.java
  │   └── UserRepository.java
  │
  ├── security/               # JWT, filter, security config
  │
  ├── service/                # Business logic layer
  │   ├── Auth/               # Authentication & Authorization logic
  │   ├── contact/            # Contact management logic
  │   ├── conversation/       # Conversation management logic
  │   ├── message/            # Message handling logic
  │   ├── Presence/           # Online/Offline presence logic
  │   ├── RefreshToken/       # Token refresh logic
  │   └── user/               # User management logic
  │
  └── utils/                  # Utility classes & helpers
```

## Global Patterns
- Exception handled via @ControllerAdvice
- Use UUID as primary key
