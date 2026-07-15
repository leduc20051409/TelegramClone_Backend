package com.leanhduc.telegramclone.dto.conversation;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateConversationRequest {
    private String title;
    private String description;
    private UUID avatarMediaId;
    private Boolean clearAvatar;
    private Boolean isPublic;
    private String username;
}
