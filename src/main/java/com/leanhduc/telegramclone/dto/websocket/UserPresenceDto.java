package com.leanhduc.telegramclone.dto.websocket;

import java.util.UUID;

public record UserPresenceDto(
        UUID userId,
        boolean online,
        Long lastSeen
) {}
