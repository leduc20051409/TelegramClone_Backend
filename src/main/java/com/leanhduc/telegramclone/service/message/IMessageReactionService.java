package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.MessageReactionDto;

import com.leanhduc.telegramclone.dto.message.ToggleReactionResult;

import java.util.UUID;

public interface IMessageReactionService {
    ToggleReactionResult toggleReaction(Long messageId, UUID userId, String reaction);
}
