package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;

import java.util.List;
import java.util.UUID;

public interface IConversationService {
    ConversationResponse getOrCreatePrivateConversation(UUID currentUserId, UUID targetUserId);
    List<UUID> getConversationMemberIds(UUID conversationId);
    public List<ConversationResponse> getAllConversationsByUser(UUID userId);
}