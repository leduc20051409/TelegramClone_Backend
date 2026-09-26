package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record MessagesReadEvent(
        UUID conversationId,
        ConversationType conversationType,
        UUID readerId,
        Long lastReadMessageId
) {}
