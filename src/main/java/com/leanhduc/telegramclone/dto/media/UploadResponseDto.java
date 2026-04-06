package com.leanhduc.telegramclone.dto.media;

import java.util.UUID;

public record UploadResponseDto(
        UUID mediaId,
        String url,
        String fileType,
        long fileSize,
        String originalFilename
) {}
