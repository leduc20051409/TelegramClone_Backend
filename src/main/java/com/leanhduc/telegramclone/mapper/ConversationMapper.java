package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.model.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(target = "lastMessage", ignore = true)
    @Mapping(target = "lastMessageTimestamp", ignore = true)
    @Mapping(target = "partnerId", ignore = true)
    ConversationResponse toResponse(Conversation conversation);
}