# WebSocket

## Protocol
- STOMP over WebSocket
- Connection endpoint: `/ws` (with SockJS fallback support).

## Authentication & Security
- Authentication is performed during the STOMP `CONNECT` phase.
- The client must send a JWT Token in the header: `Authorization: Bearer <token>`.
- The backend (`ChannelInterceptor`) intercepts the `CONNECT` frame, validates the token, and assigns the `UserId` to the `Principal` of the WebSocket session.
- If the token is invalid or expired, the connection is immediately dropped.
- Payloads sent from the client MUST NOT contain `senderId`. The server always extracts `senderId` from the `Principal` to prevent impersonation.

## Standard Payload Format (WsEnvelope)
- All messages sent/received over WebSocket must be wrapped in a common format (`WsEnvelope`) so the frontend can easily parse and handle events:
  ```json
  {
    "event": "NEW_MESSAGE",  // Event name (e.g., NEW_MESSAGE, TYPING, ERROR)
    "timestamp": 1718293847, // Epoch milliseconds
    "data": { ... }          // Actual payload (e.g., ChatMessageResponse)
  }

## Channels & Routing (Messaging)

- **Application Destination Prefix:** `/app` (Clients use this prefix to send messages TO the server).
- **User Destination Prefix:** `/user` (Used to send messages TO a specific user).
- **Topic Prefix:** `/topic` (Used for broadcasting to multiple users, e.g., group chat or presence).

### Private Chat Flow (1–1):

1. **Send message:** The client sends a message (`ChatMessageRequest`) to `/app/chat.send`.
2. **Receive message:**
  - The client subscribes to `/user/queue/chat` to listen for new messages (`NEW_MESSAGE`).
  - The server uses `SimpMessagingTemplate.convertAndSendToUser` to push messages to this destination.

## Presence Handling
- On connect → set user ONLINE
- On disconnect → set user OFFLINE

## Broadcast
- Notify contacts when status changes

## Scaling Strategy (future)
- Use Redis pub/sub if multiple instances