package com.leanhduc.telegramclone.dto.contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID contactId,
        UUID userId,
        String username,
        String displayName,
        String alias,
        Instant addedAt,
        String avatarUrl
) {}
