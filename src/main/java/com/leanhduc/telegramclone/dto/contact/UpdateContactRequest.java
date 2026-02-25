package com.leanhduc.telegramclone.dto.contact;

public record UpdateContactRequest(
        String alias,
        Boolean isMuted,
        Boolean isBlocked
) {}
