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
        Long viewCount
) {
}
