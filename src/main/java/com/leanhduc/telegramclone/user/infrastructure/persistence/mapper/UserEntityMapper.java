package com.leanhduc.telegramclone.user.infrastructure.persistence.mapper;

import com.leanhduc.telegramclone.user.domain.model.User;
import com.leanhduc.telegramclone.user.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper (componentModel = "spring")
public interface UserEntityMapper {
    User toDomain(UserEntity entity);
    UserEntity toEntity(User user);
}
