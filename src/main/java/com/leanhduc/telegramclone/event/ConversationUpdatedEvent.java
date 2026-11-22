package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record ConversationUpdatedEvent(
        UUID conversationId,
        ConversationType conversationType,
        ConversationResponse conversation
) {}
