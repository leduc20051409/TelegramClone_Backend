package com.leanhduc.telegramclone.dto.contact;

import java.util.UUID;

public record AddContactRequest(
        UUID contactId,
        String alias
) {}
