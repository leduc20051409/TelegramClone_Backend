package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record ChatMessageRequest(
        UUID conversationId,
        String message
) {
}