package com.leanhduc.telegramclone.dto.message;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        UUID conversationId,
        UUID senderId,
        String message,
        Instant createdAt
) {
}
