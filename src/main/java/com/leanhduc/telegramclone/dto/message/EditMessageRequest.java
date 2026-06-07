package com.leanhduc.telegramclone.dto.message;

public record EditMessageRequest(
        String message,
        java.util.List<java.util.UUID> mediaIds
) {
}
