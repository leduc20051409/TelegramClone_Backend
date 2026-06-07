package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record ChatTypingRequest(
        UUID conversationId,
        boolean isTyping
) {}
