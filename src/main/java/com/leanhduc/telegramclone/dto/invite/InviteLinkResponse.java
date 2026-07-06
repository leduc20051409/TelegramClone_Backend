package com.leanhduc.telegramclone.dto.invite;

import java.time.Instant;
import java.util.UUID;

public record InviteLinkResponse(
        Long id,
        UUID conversationId,
        String inviteCode,
        String name,
        Instant expireAt,
        Integer memberLimit,
        Integer currentUses,
        Boolean isRevoked,
        Boolean isPrimary,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
