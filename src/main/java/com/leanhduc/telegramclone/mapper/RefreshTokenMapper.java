package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.auth.RefreshTokenResponse;
import com.leanhduc.telegramclone.model.RefreshToken;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper (componentModel = "spring")
public interface RefreshTokenMapper {

    @Mapping (source = "token", target = "token")
    @Mapping(source = "expiresAt", target = "expiresAt")
    RefreshTokenResponse toResponse(RefreshToken entity);
}
