package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.auth.AuthResponse;
import com.leanhduc.telegramclone.dto.auth.LoginRequest;
import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.dto.auth.RegisterRequest;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import com.leanhduc.telegramclone.service.Auth.IAuthService;
import com.leanhduc.telegramclone.service.RefreshToken.IRefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final IAuthService authService;
    private final IRefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;
        User user = refreshTokenService.verifyRefreshToken(refreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        RefreshTokenResponse newRefreshData = refreshTokenService.rotateRefreshToken(refreshToken);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshData.token());
        response.setUserId(user.getId().toString());
        response.setEmail(user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;

        refreshTokenService.revokeRefreshToken(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@RequestHeader("X-User-Email") String email) {
        refreshTokenService.revokeAllTokensByUser(email);
        return ResponseEntity.noContent().build();
    }
}