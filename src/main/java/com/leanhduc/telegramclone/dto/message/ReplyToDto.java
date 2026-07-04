package com.leanhduc.telegramclone.dto.message;

public record ReplyToDto(
        Long replyToMessageId,
        String senderName,
        String message
) {
}
