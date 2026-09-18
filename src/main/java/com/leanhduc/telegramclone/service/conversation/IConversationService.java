package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.*;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import java.util.List;
import java.util.UUID;

public interface IConversationService {
    ConversationResponse getOrCreatePrivateConversation(UUID currentUserId, UUID targetUserId);
    List<UUID> getConversationMemberIds(UUID conversationId);
    List<ConversationResponse> getAllConversationsByUser(UUID userId);
    ConversationResponse createGroupConversation(UUID creatorUserId, CreateGroupRequest request);
    void leaveConversation(UUID userId, UUID conversationId);
    ConversationResponse addMember(UUID requesterId, UUID conversationId, UUID targetUserId);
    ConversationResponse updateConversation(UUID requesterId, UUID conversationId, UpdateConversationRequest request);
    void removeMember(UUID requesterId, UUID conversationId, UUID targetUserId);
    void updateMemberRole(UUID requesterId, UUID conversationId, UUID targetUserId, ConversationRole role);
    void updateMemberMute(UUID requesterId, UUID conversationId, boolean isMuted);
    ConversationType getConversationType(UUID conversationId);
    void deleteConversation(UUID requesterId, UUID conversationId);
    List<ConversationResponse> searchPublicConversations(String query);
    ConversationResponse getPublicConversationByUsername(String username);
    DiscussionGroupInfoResponse linkDiscussionGroup(UUID channelId, UUID groupId, UUID requesterId);
    void unlinkDiscussionGroup(UUID channelId, UUID requesterId);
    DiscussionGroupInfoResponse getLinkedDiscussionGroup(UUID conversationId, UUID requesterId);

    // Permissions management
    void updateDefaultPermissions(UUID requesterId, UUID conversationId, UpdateDefaultPermissionsRequest request);
    void updateMemberPermissions(UUID requesterId, UUID conversationId, UUID targetUserId, UpdateMemberPermissionsRequest request);
    void updateAdminPermissions(UUID requesterId, UUID conversationId, UUID targetUserId, UpdateAdminPermissionsRequest request);
    UserDto getMemberPermissions(UUID requesterId, UUID conversationId, UUID targetUserId);
}