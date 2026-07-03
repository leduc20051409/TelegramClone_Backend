package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        Instant createdAt,
        String lastMessage,
        Instant lastMessageTimestamp,
        UUID partnerId,
        String avatarUrl,
        UUID avatarMediaId,
        String description,
        List<UserDto> participants,
        UUID lastMessageSenderId,
        Integer unreadCount,
        List<ChatMessageResponse> pinnedMessages
) {
}