package com.leanhduc.telegramclone.service.Auth;

import com.leanhduc.telegramclone.config.CustomUserDetails;
import com.leanhduc.telegramclone.dto.auth.*;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.UserMapper;
import com.leanhduc.telegramclone.model.PasswordResetToken;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.PasswordResetTokenRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import com.leanhduc.telegramclone.service.RefreshToken.IRefreshTokenService;
import com.leanhduc.telegramclone.service.email.IEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final IRefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final IEmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = userMapper.toEntity(registerRequest);
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        return buildAuthResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequest.getEmail(),
                                loginRequest.getPassword()
                        )
                );

        CustomUserDetails userDetails =
                (CustomUserDetails) authentication.getPrincipal();

        User user = userDetails.getUser();

        return buildAuthResponse(user);

//        User user = userRepository.findByEmail(loginRequest.getEmail())
//                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
//        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
//            throw new UnauthorizedException("Invalid email or password");
//        }
//        log.info("User logged in successfully: {}", user.getEmail());
//        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse refreshAccessToken(String oldRefreshToken) {

        RefreshTokenResponse rotationResult =
                refreshTokenService.rotateRefreshToken(oldRefreshToken);

        String accessToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString(rotationResult.userId()),
                rotationResult.email(),
                rotationResult.role()
        );

        log.info("Access token refreshed for user: {}", rotationResult.email());

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(rotationResult.token());
        response.setUserId(rotationResult.userId());
        response.setEmail(rotationResult.email());
        return response;
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
        RefreshTokenResponse refreshData = refreshTokenService.createRefreshToken(user.getEmail());

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshData.token());
        response.setUserId(user.getId().toString());
        response.setEmail(user.getEmail());

        return response;
    }

    @Override
    @Transactional
    public void requestForgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail();
        log.info("Received forgot password request for email: {}", email);

        if (!userRepository.existsByEmail(email)) {
            log.warn("Forgot password request ignored: email {} does not exist", email);
            return;
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(15 * 60))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(email, resetLink);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Processing password reset for token");

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_REQUEST));

        if (resetToken.isUsed() || resetToken.getExpiryDate().isBefore(Instant.now())) {
            log.warn("Password reset rejected: Token is expired or already used");
            throw new BusinessException(ErrorCode.INVALID_RESET_REQUEST);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORDS_DO_NOT_MATCH);
        }

        User user = resetToken.getUser();

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            log.warn("Password reset rejected: New password matches current password");
            throw new BusinessException(ErrorCode.INVALID_RESET_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenService.revokeAllTokensByUser(user.getId());
        log.info("Password reset successful and all sessions revoked for user: {}", user.getEmail());
    }
}