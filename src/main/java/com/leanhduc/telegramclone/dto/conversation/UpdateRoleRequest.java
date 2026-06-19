package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.ConversationRole;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoleRequest {
    private ConversationRole role;
}
