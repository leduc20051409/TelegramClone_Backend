package com.leanhduc.telegramclone.service.RefreshToken;

import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.model.User;

import java.util.UUID;

public interface IRefreshTokenService {
    RefreshTokenResponse createRefreshToken(String email);

    User verifyRefreshToken(String tokenString);

    void revokeRefreshToken(String tokenString);

    void revokeAllTokensByUser(UUID userId);

    RefreshTokenResponse rotateRefreshToken(String oldTokenString);

    void cleanupExpiredTokens();

    Long getActiveSessionCount(String email);
}
