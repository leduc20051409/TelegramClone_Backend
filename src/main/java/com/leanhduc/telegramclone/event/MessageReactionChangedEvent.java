package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.MessageReactionDto;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.List;
import java.util.UUID;

public record MessageReactionChangedEvent(
        UUID conversationId,
        ConversationType conversationType,
        Long messageId,
        List<MessageReactionDto> reactions
) {}
