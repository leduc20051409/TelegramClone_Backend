package com.leanhduc.telegramclone.dto.websocket;

import java.util.UUID;

public record UnpinMessageResponse(
        Long messageId,
        UUID conversationId
) {
}
