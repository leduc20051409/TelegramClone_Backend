package com.leanhduc.telegramclone.mapper;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ForwardedFromDto;
import com.leanhduc.telegramclone.dto.message.ReplyToDto;
import com.leanhduc.telegramclone.model.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    @Mapping(source = "conversation.id", target = "conversationId")
    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(target = "senderName", expression = "java(mapSenderName(message.getSender()))")
    @Mapping(source = "body", target = "message")
    @Mapping(target = "media", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "replyTo", expression = "java(mapReplyTo(message.getReplyTo()))")
    @Mapping(target = "reactions", expression = "java(mapReactions(message.getReactions()))")
    @Mapping(target = "forwardedFrom", expression = "java(mapForwardedFrom(message))")
    ChatMessageResponse toResponse(Message message);

    default String mapSenderName(com.leanhduc.telegramclone.model.User sender) {
        if (sender == null) return "Unknown";
        if (sender.getDisplayName() != null && !sender.getDisplayName().isBlank()) {
            return sender.getDisplayName();
        }
        return sender.getUsername();
    }

    default ForwardedFromDto mapForwardedFrom(Message message) {
        if (message == null) return null;
        if (message.getForwardedFromConversation() == null && message.getForwardedFromUser() == null) {
            return null;
        }
        UUID convId = message.getForwardedFromConversation() != null ? message.getForwardedFromConversation().getId() : null;
        String convTitle = message.getForwardedFromConversation() != null ? message.getForwardedFromConversation().getTitle() : null;
        UUID avatarMediaId = message.getForwardedFromConversation() != null ? message.getForwardedFromConversation().getAvatarMediaId() : null;
        String convAvatar = avatarMediaId != null ? avatarMediaId.toString() : null;
        UUID senderId = message.getForwardedFromUser() != null ? message.getForwardedFromUser().getId() : null;
        String senderName = message.getForwardedFromUser() != null ? mapSenderName(message.getForwardedFromUser()) : null;

        return new ForwardedFromDto(convId, convTitle, convAvatar, senderId, senderName);
    }

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

    default List<com.leanhduc.telegramclone.dto.message.MessageReactionDto> mapReactions(List<com.leanhduc.telegramclone.model.MessageReaction> reactions) {
        if (reactions == null) return List.of();
        return reactions.stream()
                .map(r -> {
                    String username = "Unknown";
                    String displayName = null;
                    if (r.getUser() != null) {
                        username = r.getUser().getUsername();
                        displayName = r.getUser().getDisplayName();
                    }
                    return new com.leanhduc.telegramclone.dto.message.MessageReactionDto(
                            r.getId().getUserId(),
                            username,
                            displayName,
                            r.getId().getReaction()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media) {
        return toResponse(message, media, null);
    }

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media, Long viewCount) {
        return toResponse(message, media, viewCount, null);
    }

    default ChatMessageResponse toResponse(Message message, List<MediaAttachmentDto> media, Long viewCount, Integer commentCount) {
        ChatMessageResponse base = toResponse(message);
        return new ChatMessageResponse(
                base.id(),
                base.conversationId(),
                base.senderId(),
                base.senderName(),
                base.message(),
                base.createdAt(),
                media,
                base.edited(),
                base.updatedAt(),
                viewCount,
                base.messageType(),
                base.replyTo(),
                base.reactions(),
                commentCount,
                base.forwardedFrom()
        );
    }
}
