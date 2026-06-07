package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.model.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    @Mapping(source = "conversation.id", target = "conversationId")
    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "body", target = "message")
    @Mapping(target = "media", ignore = true)
    ChatMessageResponse toResponse(Message message);

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media) {
        ChatMessageResponse base = toResponse(message);
        return new ChatMessageResponse(
                base.id(),
                base.conversationId(),
                base.senderId(),
                base.message(),
                base.createdAt(),
                media,
                base.edited(),
                base.updatedAt()
        );
    }
}
