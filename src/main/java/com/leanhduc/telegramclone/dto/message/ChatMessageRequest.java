package com.leanhduc.telegramclone.dto.message;

import java.util.List;
import java.util.UUID;

public record ChatMessageRequest(
        UUID conversationId,
        String message,
        List<UUID> mediaIds
) {
}
