package com.leanhduc.telegramclone.dto.message;

public record PinMessageResult(
        ChatMessageResponse pinnedMessage,
        ChatMessageResponse systemMessage
) {
}
