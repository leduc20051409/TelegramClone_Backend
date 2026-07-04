package com.leanhduc.telegramclone.dto.websocket;

import com.leanhduc.telegramclone.dto.message.MessageReactionDto;

import java.util.List;
import java.util.UUID;

public record MessageReactionEventDto(
        Long messageId,
        UUID conversationId,
        List<MessageReactionDto> reactions
) {}
