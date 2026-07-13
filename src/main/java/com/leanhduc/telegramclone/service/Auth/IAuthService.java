package com.leanhduc.telegramclone.service.Auth;

import com.leanhduc.telegramclone.dto.auth.*;

public interface IAuthService {
    AuthResponse register(RegisterRequest registerRequest);
    AuthResponse login(LoginRequest loginRequest);
    AuthResponse refreshAccessToken(String oldRefreshToken);

    void requestForgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
