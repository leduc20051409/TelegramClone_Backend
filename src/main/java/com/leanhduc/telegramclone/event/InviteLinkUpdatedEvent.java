package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record InviteLinkUpdatedEvent(
        UUID conversationId,
        ConversationType conversationType,
        InviteLinkResponse inviteLink
) {}
