package com.leanhduc.telegramclone.user.domain.repository;

import com.leanhduc.telegramclone.user.domain.model.User;
import com.leanhduc.telegramclone.user.infrastructure.persistence.entity.UserEntity;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
}
