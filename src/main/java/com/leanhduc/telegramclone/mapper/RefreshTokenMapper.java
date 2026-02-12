package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.model.RefreshToken;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper (componentModel = "spring")
public interface RefreshTokenMapper {

    @Mapping (source = "token", target = "token")
    @Mapping(source = "expiresAt", target = "expiresAt")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.role", target = "role")
    RefreshTokenResponse toResponse(RefreshToken entity);
}
