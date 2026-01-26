package com.leanhduc.telegramclone.dto.auth;

import java.time.Instant;

public record RefreshTokenResponse(
        String token,
        Instant expiresAt
) {}