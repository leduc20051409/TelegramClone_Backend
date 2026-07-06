package com.leanhduc.telegramclone.service.invite;

import com.leanhduc.telegramclone.dto.invite.CreateInviteLinkRequest;
import com.leanhduc.telegramclone.dto.invite.InviteLinkInfoResponse;
import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;

import java.util.List;
import java.util.UUID;

public interface IConversationInviteLinkService {
    InviteLinkResponse createInviteLink(UUID requesterId, UUID conversationId, CreateInviteLinkRequest request);
    InviteLinkInfoResponse getInviteLinkInfo(String inviteCode);
    InviteLinkResponse joinConversation(UUID userId, String inviteCode);
    InviteLinkResponse revokeInviteLink(UUID requesterId, Long inviteLinkId);
    List<InviteLinkResponse> getInviteLinksForConversation(UUID requesterId, UUID conversationId);
}
