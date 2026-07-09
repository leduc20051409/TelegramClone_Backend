package com.leanhduc.telegramclone.service.Presence;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

@Getter
public class UserPresenceChangedEvent extends ApplicationEvent {
    private final UUID userId;
    private final boolean online;
    private final Instant lastSeen;

    public UserPresenceChangedEvent(Object source, UUID userId, boolean online, Instant lastSeen) {
        super(source);
        this.userId = userId;
        this.online = online;
        this.lastSeen = lastSeen;
    }
}
