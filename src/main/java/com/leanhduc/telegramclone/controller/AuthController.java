package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.auth.AuthResponse;
import com.leanhduc.telegramclone.dto.auth.LoginRequest;
import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.dto.auth.RegisterRequest;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import com.leanhduc.telegramclone.service.Auth.IAuthService;
import com.leanhduc.telegramclone.service.RefreshToken.IRefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import static com.leanhduc.telegramclone.utils.CookieUtil.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final IAuthService authService;
    private final IRefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.getRefreshToken());
        authResponse.setRefreshToken(null);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);

        User user = refreshTokenService.verifyRefreshToken(refreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        RefreshTokenResponse newRefreshData = refreshTokenService.rotateRefreshToken(refreshToken);

        setRefreshTokenCookie(response, newRefreshData.token());

        AuthResponse authResponse = new AuthResponse();
        authResponse.setAccessToken(newAccessToken);
        authResponse.setRefreshToken(null);
        authResponse.setUserId(user.getId().toString());
        authResponse.setEmail(user.getEmail());

        return ResponseEntity.ok(authResponse);
    }


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookie(request);
        refreshTokenService.revokeRefreshToken(refreshToken);
        clearRefreshTokenCookie(response);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @RequestHeader("X-User-Email") String email,
            HttpServletResponse response) {
        refreshTokenService.revokeAllTokensByUser(email);
        clearRefreshTokenCookie(response);

        return ResponseEntity.noContent().build();
    }
}