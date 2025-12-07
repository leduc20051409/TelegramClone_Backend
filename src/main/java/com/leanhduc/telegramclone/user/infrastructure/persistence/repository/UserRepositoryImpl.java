package com.leanhduc.telegramclone.user.infrastructure.persistence.repository;

import com.leanhduc.telegramclone.shared.domain.exception.ResourceNotFoundException;
import com.leanhduc.telegramclone.user.domain.model.User;
import com.leanhduc.telegramclone.user.domain.repository.UserRepository;
import com.leanhduc.telegramclone.user.infrastructure.persistence.jpa.UserJpaRepository;
import com.leanhduc.telegramclone.user.infrastructure.persistence.mapper.UserEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;
    private final UserEntityMapper userEntityMapper;

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(userEntityMapper::toDomain);
    }
}
