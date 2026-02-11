package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.user.UpdateProfileRequest;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.dto.user.UserSummaryDto;
import com.leanhduc.telegramclone.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final IUserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        UserDto userDto = userService.getCurrentUser();
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserSummaryDto> getUserById(@PathVariable UUID userId) {
        UserSummaryDto userSummaryDto = userService.getUserById(userId);
        return ResponseEntity.ok(userSummaryDto);
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateProfile(@RequestBody UpdateProfileRequest request) {
        UserDto userDto = userService.updateProfile(request);
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserSummaryDto>> searchUsers(
            @RequestParam String query,
            Pageable pageable) {
        Page<UserSummaryDto> users = userService.searchUsers(query, pageable);
        return ResponseEntity.ok(users);
    }
}
