package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.List;
import java.util.UUID;

public record MessagesForwardedEvent(
        List<TargetBroadcastDto> broadcasts
) {
    public record TargetBroadcastDto(
            UUID conversationId,
            ConversationType conversationType,
            List<UUID> memberIds,
            ChatMessageResponse messageResponse
    ) {}
}
