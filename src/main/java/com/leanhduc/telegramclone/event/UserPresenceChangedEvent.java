package com.leanhduc.telegramclone.event;

import java.time.Instant;
import java.util.UUID;

public record UserPresenceChangedEvent(
        UUID userId,
        boolean online,
        Instant lastSeen
) {}
