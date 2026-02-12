package com.leanhduc.telegramclone.service.Auth;

import com.leanhduc.telegramclone.dto.auth.AuthResponse;
import com.leanhduc.telegramclone.dto.auth.LoginRequest;
import com.leanhduc.telegramclone.dto.auth.RegisterRequest;

public interface IAuthService {
    AuthResponse register(RegisterRequest registerRequest);
    AuthResponse login(LoginRequest loginRequest);

    AuthResponse refreshAccessToken(String oldRefreshToken);
}
