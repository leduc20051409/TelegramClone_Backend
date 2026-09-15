package com.leanhduc.telegramclone.dto.message;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ForwardMessageRequest(
        @NotEmpty(message = "Target conversation IDs cannot be empty")
        List<UUID> targetConversationIds
) {}
