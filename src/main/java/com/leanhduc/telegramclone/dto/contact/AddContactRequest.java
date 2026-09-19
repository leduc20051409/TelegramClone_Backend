package com.leanhduc.telegramclone.dto.contact;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddContactRequest(
        @NotNull(message = "Contact ID is required")
        UUID contactId,
        String alias
) {}
