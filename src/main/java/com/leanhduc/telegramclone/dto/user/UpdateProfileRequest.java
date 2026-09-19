package com.leanhduc.telegramclone.dto.user;

import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    @Size(max = 70, message = "Display name must not exceed 70 characters")
    private String displayName;

    @Size(max = 255, message = "Bio must not exceed 255 characters")
    private String bio;

    private UUID avatarMediaId;
}
