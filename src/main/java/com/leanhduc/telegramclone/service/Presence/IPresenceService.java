package com.leanhduc.telegramclone.service.Presence;

import java.util.UUID;

public interface IPresenceService {
    void connect(UUID userId, String sessionId);

    void heartbeat(UUID userId);

    void disconnect(UUID userId, String sessionId);

    boolean isUserOnline(UUID userId);
}
