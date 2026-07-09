package com.leanhduc.telegramclone.dto.user;

import lombok.Data;

import java.util.UUID;

@Data
public class UserSummaryDto {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean online;
    private java.time.Instant lastSeen;
}
