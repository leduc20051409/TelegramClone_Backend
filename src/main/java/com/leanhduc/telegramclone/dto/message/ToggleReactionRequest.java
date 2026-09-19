package com.leanhduc.telegramclone.dto.message;

import jakarta.validation.constraints.NotBlank;

public record ToggleReactionRequest(
        @NotBlank(message = "Reaction emoji must not be blank")
        String reaction
) {}
