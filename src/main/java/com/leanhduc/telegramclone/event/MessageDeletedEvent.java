package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.List;
import java.util.UUID;

public record MessageDeletedEvent(
        Long messageId,
        UUID conversationId,
        ConversationType conversationType,
        List<UUID> memberIds
) {}
