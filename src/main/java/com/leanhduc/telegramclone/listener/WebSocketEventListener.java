package com.leanhduc.telegramclone.listener;

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
public class WebSocketEventListener {
    private final IPresenceService presenceService;
    private final ITypingService typingService;

    // 1. Xử lý khi User Connect
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        // Lấy Session ID của kết nối WebSocket hiện tại
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            UUID userId = UUID.fromString(principal.getName());

            // Truyền cả 2 tham số vào hàm connect của bạn
            presenceService.connect(userId, sessionId);
            log.info("User Connect: ID={}, Session={}", userId, sessionId);
        }
    }

    // 2. Xử lý khi User Disconnect
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        // Lấy Session ID của tab/thiết bị vừa ngắt kết nối
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            UUID userId = UUID.fromString(principal.getName());

            // Truyền cả 2 tham số vào hàm disconnect của bạn
            presenceService.disconnect(userId, sessionId);
            log.info("User Disconnect: ID={}, Session={}", userId, sessionId);

            // Xóa tất cả các trạng thái đang gõ phím của user này trong Redis
            typingService.clearTypingForUser(userId);
        }
    }
}
