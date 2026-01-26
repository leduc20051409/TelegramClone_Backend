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

    @Override
    @Transactional(readOnly = true)
    public User verifyRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findById(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            if (!refreshToken.isRevoked()) {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
            }
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
    public void revokeAllTokensByUser(String email) {
        refreshTokenRepository.revokeAllByUserEmail(email);
        log.info("Revoked all tokens for user: {}", email);
    }

    @Override
    @Transactional
    public RefreshTokenResponse rotateRefreshToken(String oldToken) {
        RefreshToken oldRefreshToken = refreshTokenRepository.findById(oldToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!oldRefreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        // Revoke old token
        oldRefreshToken.setRevoked(true);
        refreshTokenRepository.save(oldRefreshToken);

        // Create new token
        String email = oldRefreshToken.getUser().getEmail();
        log.info("Rotating refresh token for user: {}", email);
        return createRefreshToken(email);
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