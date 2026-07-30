# Presence Module

## Responsibilities
- Track real-time user online/offline status using Redis (`PresenceService`).
- Maintain session presence TTL and refresh status via heartbeats.
- Update `last_seen` timestamp in PostgreSQL upon total disconnect.
- Broadcast real-time presence changes (`USER_PRESENCE_CHANGED`) to interested conversation members via WebSocket.

## Architecture & Workflow

### 1. Redis State Management
- Online presence is maintained in Redis keys: `user:presence:{userId}` with a TTL (e.g. 60 seconds).
- Active WebSocket connections continuously extend TTL.
- A user is marked **ONLINE** as long as at least one active connection key exists in Redis.

### 2. Disconnect & Database Synchronization
- When all active sessions for a user disconnect, the Redis key expires or is removed.
- `PresenceService` updates `users.last_seen = Instant.now()` in PostgreSQL.

### 3. Real-Time Broadcast (`USER_PRESENCE_CHANGED`)
- Presence state transitions (ONLINE -> OFFLINE or vice versa) emit a `UserPresenceChangedEvent`.
- `PresenceController` listens for events and broadcasts `WsEnvelope<PresenceEvent>` to contacts and conversation members:
  ```json
  {
    "event": "USER_PRESENCE_CHANGED",
    "timestamp": 1760000000000,
    "data": {
      "userId": "770e8400-e29b-41d4-a716-446655440002",
      "isOnline": false,
      "lastSeen": "2026-03-06T10:30:45Z"
    }
  }
  ```