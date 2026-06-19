package com.leanhduc.telegramclone.dto.conversation;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMemberRequest {
    private UUID userId;
}
