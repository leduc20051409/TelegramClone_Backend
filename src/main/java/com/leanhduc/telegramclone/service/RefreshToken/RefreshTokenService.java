package com.leanhduc.telegramclone.service.RefreshToken;

import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.exception.UnauthorizedException;
import com.leanhduc.telegramclone.mapper.RefreshTokenMapper;
import com.leanhduc.telegramclone.model.RefreshToken;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.RefreshTokenRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements IRefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenMapper refreshTokenMapper;

    @Value("${jwt.refreshTokenExpirationMs}")
    private Long refreshTokenDurationMs;

    @Override
    public RefreshTokenResponse createRefreshToken(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        cleanupOldTokensForUser(user);

        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setRevoked(false);

        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user: {}", email);

        return refreshTokenMapper.toResponse(savedToken);
    }

    private void cleanupOldTokensForUser(User user) {
        var activeTokens = refreshTokenRepository.findValidTokensByUser(user, Instant.now());

        if (activeTokens.size() >= 3) {
            activeTokens.sort(Comparator.comparing(RefreshToken::getCreatedAt));
            for (int i = 0; i < activeTokens.size() - 2; i++) {
                RefreshToken oldToken = activeTokens.get(i);
                oldToken.setRevoked(true);
                refreshTokenRepository.save(oldToken);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User verifyRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }
        RefreshToken refreshToken = refreshTokenRepository.findById(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        return refreshToken.getUser();
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findById(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        log.info("Revoked refresh token: {}", token);
    }

    @Override
    @Transactional
    public void revokeAllTokensByUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked all tokens for user: {}", userId);
    }

    @Override
    @Transactional
    public RefreshTokenResponse rotateRefreshToken(String oldToken) {
        User user = verifyRefreshToken(oldToken);
        revokeRefreshToken(oldToken);
        return createRefreshToken(user.getEmail());
    }

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.info("Cleaned up expired and revoked refresh tokens");
    }

    @Override
    @Transactional(readOnly = true)
    public Long getActiveSessionCount(String email) {
        return refreshTokenRepository.countActiveSessionsByEmail(email, Instant.now());
    }
}