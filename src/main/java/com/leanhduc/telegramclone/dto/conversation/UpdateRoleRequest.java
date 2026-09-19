package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.ConversationRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoleRequest {
    @NotNull(message = "Role is required")
    private ConversationRole role;
}
