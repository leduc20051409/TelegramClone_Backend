# WebSocket

## Protocol
- STOMP over WebSocket

## Presence Handling
- On connect → set user ONLINE
- On disconnect → set user OFFLINE

## Broadcast
- Notify contacts when status changes

## Scaling Strategy (future)
- Use Redis pub/sub if multiple instances