package com.leanhduc.telegramclone.service.user;

import com.leanhduc.telegramclone.dto.user.UpdateProfileRequest;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.dto.user.UserSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface IUserService {
    UserDto getCurrentUser();
    UserSummaryDto getUserById(UUID userId);
    UserDto updateProfile(UpdateProfileRequest request);
    Page<UserSummaryDto> searchUsers(String query, Pageable pageable);
}
