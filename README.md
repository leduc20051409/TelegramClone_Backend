![TelegramClone](https://socialify.git.ci/leduc20051409/TelegramClone/image?custom_language=Java&font=Inter&forks=1&issues=1&language=1&name=1&owner=1&pattern=Overlapping+Hexagons&stargazers=1&theme=Dark)
# ✈️ TelegramClone Backend

A real-time messaging and chat platform backend (Telegram Clone) built with Spring Boot.

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-black.svg)](https://sonarcloud.io/project/overview?id=leduc20051409_TelegramClone_Backend)

## Introduction

Welcome to **TelegramClone** backend. This project is the backend service for a real-time messaging platform. It is built with **Spring Boot 3.5** and **Java 21**, leveraging **WebSockets + STOMP** for real-time channels, **Redis** for state caching & user presence tracking, and **PostgreSQL** for persistent storage.


## System Requirements

To run this backend project, you will need to have the following tools installed:

- **Java Development Kit (JDK) 21** or higher.
- **Maven** build tool.
- **PostgreSQL 16** database.
- **Redis 7** for data caching and user presence.
- **Docker** with **Docker Compose**.


## Key Features

The backend is designed with a lightweight hexagonal & DDD-inspired architecture, ensuring security, scalability, and real-time synchronization.

### Real-Time & Communication Features

- **Real-Time Messaging Gateway**: Delivered instantly without manual client refreshing via WebSockets and STOMP.
- **User Presence Tracking**: Real-time online/offline indicators using Redis to prevent database bottlenecks from client polling.
- **Dynamic Group Invites**: Generate and revoke group invitation URLs instantly.
- **Channel Announcements**: Unique view counters powered by Redis Sets to prevent spamming and view manipulation.

### Performance & Security

- **Secure Gateways**: Extracts user identity from JWT connection tokens server-side during the WebSocket handshake to prevent sender impersonation.
- **Cursor-Based Pagination**: Optimized chat history loading to eliminate duplicate messages and database lag under large volumes.
- **Media Upload Pipeline**: Flags uploaded media as temporary and activates them only when the corresponding message is delivered, avoiding storage bloat.


## Getting Started

Follow these steps to set up and run the backend.

### Method 1: Run Locally (Development Mode)

#### 1. Clone the repository:

```bash
git clone https://github.com/leduc20051409/TelegramClone.git
cd TelegramClone
```

#### 2. Configure the Environments:

- Copy the `.env.example` template to `.env.dev` and update it with your database, Redis, and Cloudinary credentials.

#### 3. Run the Backend:

```bash
# Build the project
mvn clean install

# Run application using the 'dev' profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

### Method 2: Run Using Docker Compose

Docker Compose builds the Spring Boot app, spins up PostgreSQL, and runs Redis automatically.

#### 1. Prepare environment variables:
Create a `.env` file in the backend root directory (based on `.env.example`).

#### 2. Build and start containers:

```bash
docker compose up --build
```


## API Documentation

The backend exposes interactive Swagger / OpenAPI docs. Once the application is running, you can access it at:
```text
http://localhost:8080/swagger-ui/index.html
```


## Contributing

If you would like to contribute to the development of this project, please follow our contribution guidelines.

![Alt](https://repobeats.axiom.co/api/embed/fd7fd76dafe452bdb7c2bc12856bd45c277ee732.svg "Repobeats analytics image")


## License

This project is licensed under the [`MIT License`](LICENSE).

```text
MIT License
Copyright (c) 2026 Le Anh Duc
```
