package com.leanhduc.telegramclone.dto.invite;

import java.util.UUID;

public record InviteLinkInfoResponse(
        String inviteCode,
        UUID conversationId,
        String title,
        String description,
        String avatarUrl,
        Integer memberCount
) {}
