package com.leanhduc.telegramclone.dto.auth;

import lombok.Builder;

import java.time.Instant;
@Builder
public record RefreshTokenResponse(
        String token,
        Instant expiresAt,
        String userId,
        String email,
        String role
) {}