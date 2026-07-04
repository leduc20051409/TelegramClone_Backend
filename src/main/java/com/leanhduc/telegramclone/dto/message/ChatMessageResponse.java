package com.leanhduc.telegramclone.dto.message;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        UUID conversationId,
        UUID senderId,
        String message,
        Instant createdAt,
        List<MediaAttachmentDto> media,
        boolean edited,
        Instant updatedAt,
        Long viewCount,
        String messageType,
        ReplyToDto replyTo,
        List<MessageReactionDto> reactions
) {
    public ChatMessageResponse(Long id, UUID conversationId, UUID senderId, String message, Instant createdAt,
                               List<MediaAttachmentDto> media, boolean edited, Instant updatedAt,
                               Long viewCount, String messageType, ReplyToDto replyTo) {
        this(id, conversationId, senderId, message, createdAt, media, edited, updatedAt, viewCount, messageType, replyTo, List.of());
    }
}
