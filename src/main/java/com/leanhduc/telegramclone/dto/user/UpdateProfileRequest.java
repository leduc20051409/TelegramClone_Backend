package com.leanhduc.telegramclone.dto.user;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String displayName;
    private String bio;
    private UUID avatarMediaId;
}
