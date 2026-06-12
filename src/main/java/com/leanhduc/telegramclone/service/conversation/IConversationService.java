package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;

import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import java.util.List;
import java.util.UUID;

public interface IConversationService {
    ConversationResponse getOrCreatePrivateConversation(UUID currentUserId, UUID targetUserId);
    List<UUID> getConversationMemberIds(UUID conversationId);
    List<ConversationResponse> getAllConversationsByUser(UUID userId);
    ConversationResponse createGroupConversation(UUID creatorUserId, CreateGroupRequest request);
}