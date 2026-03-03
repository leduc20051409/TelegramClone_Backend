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
  │   │   └── PresenceController.java   # Xử lý realtime presence (online/offline)
  │   │
  │   ├── AuthController.java           # API đăng nhập / đăng ký / refresh token
  │   ├── ContactController.java        # API quản lý danh bạ
  │   └── UserController.java           # API quản lý thông tin user
  │
  ├── dto/                    # Data Transfer Object (Request / Response)
  │   ├── auth/               # DTO cho authentication
  │   ├── contact/            # DTO cho contact
  │   └── user/
  │       ├── UpdateProfileRequest.java
  │       ├── UserDto.java
  │       └── UserSummaryDto.java
  │
  ├── exception/              # Xử lý exception toàn cục (GlobalExceptionHandler, custom exception)
  │
  ├── listener/               # Event listener (ApplicationEvent, WebSocket event, etc.)
  │
  ├── mapper/                 # MapStruct / manual mapper (Entity ↔ DTO)
  │
  ├── model/                  # Entity (JPA entities mapping database tables)
  │
  ├── repository/             # Data Access Layer (Spring Data JPA)
  │   ├── ContactRepository.java
  │   ├── RefreshTokenRepository.java
  │   └── UserRepository.java
  │
  ├── security/               # JWT, filter, security config
  │
  └── service/                # Business logic layer
  ├── auth/
  ├── contact/
  └── presence/
```

## Global Patterns
- Exception handled via @ControllerAdvice
- Use UUID as primary key