package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.ConversationType;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        Instant createdAt
) {
}