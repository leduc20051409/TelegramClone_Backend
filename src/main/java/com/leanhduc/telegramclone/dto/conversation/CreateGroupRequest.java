package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.ConversationType;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGroupRequest {
    private String title;
    private String description;
    private UUID avatarMediaId;
    private List<UUID> memberIds;
    private ConversationType type; // GROUP or CHANNEL
}
