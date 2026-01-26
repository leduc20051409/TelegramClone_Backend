package com.leanhduc.telegramclone.dto.user;

import com.leanhduc.telegramclone.model.enums.RoleUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String username;
    private String displayName;
    private String email;
    private String bio;
    private UUID avatarMediaId;
    private RoleUser role;
}
