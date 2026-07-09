package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.auth.RegisterRequest;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.dto.user.UserSummaryDto;
import com.leanhduc.telegramclone.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "avatarMediaId", ignore = true)
    @Mapping(target = "bio", ignore = true)
    User toEntity(RegisterRequest request);


    @Mapping(target = "online", ignore = true)
    UserDto toDto(User user);

    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "online", ignore = true)
    UserSummaryDto toSummaryDto(User user);
}
