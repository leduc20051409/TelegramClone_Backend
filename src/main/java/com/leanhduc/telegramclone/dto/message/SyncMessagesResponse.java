package com.leanhduc.telegramclone.dto.message;

import java.util.List;

public record SyncMessagesResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        Long nextAfterId
) {
    public SyncMessagesResponse {
        if (messages == null) {
            messages = List.of();
        }
    }
}
