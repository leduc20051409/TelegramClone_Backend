package com.leanhduc.telegramclone.dto.conversation;

import java.util.UUID;

public record SlowModeStatusResponse(
        UUID conversationId,
        Integer slowModeDelaySeconds,
        Long remainingCooldownSeconds
) {}
