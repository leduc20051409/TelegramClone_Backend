package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record ChatTypingResponse(
        UUID conversationId,
        UUID userId,
        boolean isTyping
) {}
