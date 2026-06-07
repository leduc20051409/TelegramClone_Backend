package com.leanhduc.telegramclone.dto.websocket;

import java.util.UUID;

public record DeleteMessageResponse(
        Long messageId,
        UUID conversationId
) {
}
