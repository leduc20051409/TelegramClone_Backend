package com.leanhduc.telegramclone.dto.conversation;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkDiscussionGroupRequest(
    @NotNull(message = "GroupId is required")
    UUID groupId
) {}
