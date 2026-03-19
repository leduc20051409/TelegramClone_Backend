package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record ChatReadRequest(
        UUID conversationId,
        Long lastReadMessageId
) {
}