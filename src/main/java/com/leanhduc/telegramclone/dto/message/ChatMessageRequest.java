package com.leanhduc.telegramclone.dto.message;

import java.util.List;
import java.util.UUID;

public record ChatMessageRequest(
        UUID conversationId,
        String message,
        List<UUID> mediaIds,
        Long replyToMessageId,
        String clientMessageId
) {
    public ChatMessageRequest(UUID conversationId, String message, List<UUID> mediaIds) {
        this(conversationId, message, mediaIds, null, null);
    }

    public ChatMessageRequest(UUID conversationId, String message, List<UUID> mediaIds, Long replyToMessageId) {
        this(conversationId, message, mediaIds, replyToMessageId, null);
    }
}
