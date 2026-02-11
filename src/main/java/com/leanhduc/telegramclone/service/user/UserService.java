package com.leanhduc.telegramclone.service.user;

import com.leanhduc.telegramclone.dto.user.UpdateProfileRequest;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.dto.user.UserSummaryDto;
import com.leanhduc.telegramclone.exception.NotFoundException;
import com.leanhduc.telegramclone.mapper.UserMapper;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService{
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId).
                orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        return userMapper.toDto(user);
    }

    @Override
    public UserSummaryDto getUserById(UUID userId) {
        User user = userRepository.findById(userId).
                orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        return userMapper.toSummaryDto(user);
    }

    @Override
    public UserDto updateProfile(UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId).
                orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getAvatarMediaId() != null) {
            user.setAvatarMediaId(request.getAvatarMediaId());
        }

        return userMapper.toDto(userRepository.save(user));
    }

    @Override
    public Page<UserSummaryDto> searchUsers(String query, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = (UUID) auth.getPrincipal();
        Page<User> users = userRepository.searchUsers(query, userId, pageable);
        return users.map(userMapper::toSummaryDto);
    }
}
