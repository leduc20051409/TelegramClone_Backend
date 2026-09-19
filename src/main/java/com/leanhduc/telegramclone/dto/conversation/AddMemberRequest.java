package com.leanhduc.telegramclone.dto.conversation;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMemberRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
}
