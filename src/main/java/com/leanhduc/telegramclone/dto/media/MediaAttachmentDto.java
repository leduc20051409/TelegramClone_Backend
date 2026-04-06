package com.leanhduc.telegramclone.dto.media;

import java.util.UUID;

public record MediaAttachmentDto(
        UUID id,
        String url,
        String mimeType,
        String fileName,
        long fileSize
) {
}
