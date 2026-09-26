package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

public record MessageCreatedEvent(
        ChatMessageResponse message,
        ConversationType conversationType
) {}
