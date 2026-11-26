package com.leanhduc.telegramclone.dto.media;

import java.math.BigDecimal;
import java.util.UUID;

public record MediaAttachmentDto(
        UUID id,
        String url,
        String mimeType,
        String fileName,
        long fileSize,
        String resourceType,
        Integer width,
        Integer height,
        BigDecimal durationSeconds
) {
    public MediaAttachmentDto(UUID id, String url, String mimeType, String fileName, long fileSize) {
        this(id, url, mimeType, fileName, fileSize, null, null, null, null);
    }
}
