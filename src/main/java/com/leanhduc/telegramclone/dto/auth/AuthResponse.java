package com.leanhduc.telegramclone.dto.auth;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String email;
}
