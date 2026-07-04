package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ReplyToDto;
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
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "replyTo", expression = "java(mapReplyTo(message.getReplyTo()))")
    ChatMessageResponse toResponse(Message message);

    default ReplyToDto mapReplyTo(Message replyTo) {
        if (replyTo == null) return null;
        String senderName = "Unknown";
        if (replyTo.getSender() != null) {
            senderName = replyTo.getSender().getDisplayName();
            if (senderName == null || senderName.isBlank()) {
                senderName = replyTo.getSender().getUsername();
            }
        }
        return new ReplyToDto(replyTo.getId(), senderName, replyTo.getBody());
    }

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media) {
        return toResponse(message, media, null);
    }

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media, Long viewCount) {
        ChatMessageResponse base = toResponse(message);
        return new ChatMessageResponse(
                base.id(),
                base.conversationId(),
                base.senderId(),
                base.message(),
                base.createdAt(),
                media,
                base.edited(),
                base.updatedAt(),
                viewCount,
                base.messageType(),
                base.replyTo()
        );
    }
}
