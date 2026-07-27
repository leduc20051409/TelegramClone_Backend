package com.leanhduc.telegramclone.dto.websocket;

import java.util.UUID;

public record MemberEventResponse(
        UUID conversationId,
        UUID userId,
        String username,
        String displayName,
        String avatarUrl
) {}
