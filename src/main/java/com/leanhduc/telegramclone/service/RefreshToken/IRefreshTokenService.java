package com.leanhduc.telegramclone.service.RefreshToken;

import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.model.User;

public interface IRefreshTokenService {
    RefreshTokenResponse createRefreshToken(String email);

    User verifyRefreshToken(String tokenString);

    void revokeRefreshToken(String tokenString);

    void revokeAllTokensByUser(String email);

    RefreshTokenResponse rotateRefreshToken(String oldTokenString);

    void cleanupExpiredTokens();

    Long getActiveSessionCount(String email);
}
