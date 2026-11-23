package com.leanhduc.telegramclone.listener.websocket;

import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import com.leanhduc.telegramclone.service.typing.ITypingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketSessionListener {
    private final IPresenceService presenceService;
    private final ITypingService typingService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            UUID userId = UUID.fromString(principal.getName());
            presenceService.connect(userId, sessionId);
            log.info("User Connect: ID={}, Session={}", userId, sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            UUID userId = UUID.fromString(principal.getName());
            presenceService.disconnect(userId, sessionId);
            log.info("User Disconnect: ID={}, Session={}", userId, sessionId);

            typingService.clearTypingForUser(userId);
        }
    }
}
