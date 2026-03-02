package com.leanhduc.telegramclone.controller.websocket;

import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PresenceController {
    private final IPresenceService presenceService;

    @MessageMapping ("/presence/heartbeat")
    public void handleHeartbeat(Principal principal) {
        if (principal != null) {
            UUID userId = UUID.fromString(principal.getName());
            presenceService.heartbeat(userId);
        }
    }
}
