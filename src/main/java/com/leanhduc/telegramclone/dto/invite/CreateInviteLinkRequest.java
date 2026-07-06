package com.leanhduc.telegramclone.dto.invite;

import java.time.Instant;

public record CreateInviteLinkRequest(
        String name,
        Instant expireAt,
        Integer memberLimit,
        Boolean isPrimary
) {}
