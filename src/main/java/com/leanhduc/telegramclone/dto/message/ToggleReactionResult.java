package com.leanhduc.telegramclone.dto.message;

import java.util.List;
import java.util.UUID;

public record ToggleReactionResult(
        UUID conversationId,
        List<MessageReactionDto> reactions
) {}
