package com.leanhduc.telegramclone.service.typing;

import java.util.Set;
import java.util.UUID;

public interface ITypingService {
    void setTyping(UUID conversationId, UUID userId, boolean isTyping);
    Set<UUID> getTypingUsers(UUID conversationId);
    void clearTypingForUser(UUID userId);
}
