package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record MessageReactionDto(
        UUID userId,
        String username,
        String displayName,
        String reaction
) {}
