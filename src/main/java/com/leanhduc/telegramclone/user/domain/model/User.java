package com.leanhduc.telegramclone.user.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private UUID id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String phone;
    private String email;
    private String bio;
    private UUID avatarMediaId;
    private RoleUser role;

    // Business methods
    public void changeDisplayName(String newName) {
        this.displayName = newName;
    }

    public void updatePassword(String hashedPassword) {
        this.passwordHash = hashedPassword;
    }
}