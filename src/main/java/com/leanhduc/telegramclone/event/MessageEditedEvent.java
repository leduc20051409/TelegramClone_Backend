package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.List;
import java.util.UUID;

public record MessageEditedEvent(
        ChatMessageResponse updatedMessage,
        ConversationType conversationType,
        List<UUID> memberIds
) {}
