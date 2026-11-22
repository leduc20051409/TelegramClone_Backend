package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record MessagePinnedEvent(
        UUID conversationId,
        ConversationType conversationType,
        ChatMessageResponse pinnedMessage,
        ChatMessageResponse systemMessage
) {}
