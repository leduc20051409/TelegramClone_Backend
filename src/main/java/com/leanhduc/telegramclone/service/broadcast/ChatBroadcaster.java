package com.leanhduc.telegramclone.service.broadcast;

import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public interface ChatBroadcaster {
    void broadcast(UUID conversationId, ConversationType type, WsEnvelope<?> envelope);
    void broadcastExcept(UUID conversationId, ConversationType type, UUID excludedUserId, WsEnvelope<?> envelope);
}
