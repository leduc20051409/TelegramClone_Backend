package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record MessageUnpinnedEvent(
        UUID conversationId,
        ConversationType conversationType,
        Long messageId
) {}
