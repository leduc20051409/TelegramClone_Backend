package com.leanhduc.telegramclone.dto.conversation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetSlowModeRequest(
        @NotNull(message = "Slow mode delay cannot be null")
        @Min(value = 0, message = "Slow mode delay must be non-negative")
        @Max(value = 86400, message = "Slow mode delay cannot exceed 24 hours (86400 seconds)")
        Integer seconds
) {}
