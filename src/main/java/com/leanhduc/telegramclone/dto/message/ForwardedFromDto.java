package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record ForwardedFromDto(
        UUID conversationId,
        String conversationTitle,
        String conversationAvatarUrl,
        UUID senderId,
        String senderName
) {}
