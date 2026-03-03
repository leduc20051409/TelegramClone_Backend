# Presence Module

## Responsibilities
- Track user connections using Redis (`connect`)
- Extend user session TTL on heartbeat (`heartbeat`)
- Remove user session on disconnect and update last seen if no sessions remain (`disconnect`)
- Check if a user is currently online (`isUserOnline`)

## Rules
- Online status is stored in Redis with a 60-second TTL, refreshed by heartbeat.
- A user is considered online if at least one active session exists in Redis.
- Last seen timestamp in the database is updated only when the last session disconnects.
- Session management is based on unique session IDs (e.g., WebSocket session IDs).