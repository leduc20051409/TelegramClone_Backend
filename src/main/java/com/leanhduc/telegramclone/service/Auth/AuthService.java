package com.leanhduc.telegramclone.service.Auth;

import com.leanhduc.telegramclone.dto.auth.AuthResponse;
import com.leanhduc.telegramclone.dto.auth.LoginRequest;
import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.dto.auth.RegisterRequest;
import com.leanhduc.telegramclone.exception.UnauthorizedException;
import com.leanhduc.telegramclone.mapper.UserMapper;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import com.leanhduc.telegramclone.service.RefreshToken.IRefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.leanhduc.telegramclone.exception.BadRequestException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final IRefreshTokenService refreshTokenService;
    private final UserMapper userMapper;

    @Override
    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email already exists");
        }
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        User user = userMapper.toEntity(registerRequest);
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        return buildAuthResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        log.info("User logged in successfully: {}", user.getEmail());
        return buildAuthResponse(user);
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
}