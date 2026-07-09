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
    private String groupRole;
    private boolean online;
    private java.time.Instant lastSeen;

    public UserDto(UUID id, String username, String displayName, String email, String bio, UUID avatarMediaId, RoleUser role) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.bio = bio;
        this.avatarMediaId = avatarMediaId;
        this.role = role;
        this.groupRole = null;
        this.online = false;
        this.lastSeen = null;
    }

    public UserDto(UUID id, String username, String displayName, String email, String bio, UUID avatarMediaId, RoleUser role, String groupRole) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.bio = bio;
        this.avatarMediaId = avatarMediaId;
        this.role = role;
        this.groupRole = groupRole;
        this.online = false;
        this.lastSeen = null;
    }
}

