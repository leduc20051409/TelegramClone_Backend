package com.leanhduc.telegramclone.dto.contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID contactId,
        String username,
        String displayName,
        String alias,
        Instant addedAt,
        String avatarUrl
) {}
