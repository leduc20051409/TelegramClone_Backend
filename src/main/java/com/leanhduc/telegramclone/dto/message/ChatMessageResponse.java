package com.leanhduc.telegramclone.dto.message;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        Long id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String message,
        Instant createdAt,
        List<MediaAttachmentDto> media,
        boolean edited,
        Instant updatedAt,
        Long viewCount,
        String messageType,
        ReplyToDto replyTo,
        List<MessageReactionDto> reactions,
        Integer commentCount,
        ForwardedFromDto forwardedFrom
) {
    public ChatMessageResponse(Long id, UUID conversationId, UUID senderId, String senderName, String message, Instant createdAt,
                               List<MediaAttachmentDto> media, boolean edited, Instant updatedAt,
                               Long viewCount, String messageType, ReplyToDto replyTo) {
        this(id, conversationId, senderId, senderName, message, createdAt, media, edited, updatedAt, viewCount, messageType, replyTo, List.of(), null, null);
    }

    public ChatMessageResponse(Long id, UUID conversationId, UUID senderId, String senderName, String message, Instant createdAt,
                               List<MediaAttachmentDto> media, boolean edited, Instant updatedAt,
                               Long viewCount, String messageType, ReplyToDto replyTo, List<MessageReactionDto> reactions) {
        this(id, conversationId, senderId, senderName, message, createdAt, media, edited, updatedAt, viewCount, messageType, replyTo, reactions, null, null);
    }

    public ChatMessageResponse(Long id, UUID conversationId, UUID senderId, String senderName, String message, Instant createdAt,
                               List<MediaAttachmentDto> media, boolean edited, Instant updatedAt,
                               Long viewCount, String messageType, ReplyToDto replyTo, List<MessageReactionDto> reactions, Integer commentCount) {
        this(id, conversationId, senderId, senderName, message, createdAt, media, edited, updatedAt, viewCount, messageType, replyTo, reactions, commentCount, null);
    }
}
