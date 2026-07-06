package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;
import com.leanhduc.telegramclone.model.ConversationInviteLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InviteLinkMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "createdBy", source = "createdBy.id")
    InviteLinkResponse toResponse(ConversationInviteLink inviteLink);
}
