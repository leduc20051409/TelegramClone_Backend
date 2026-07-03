package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;

import com.leanhduc.telegramclone.dto.message.EditMessageRequest;

import java.util.List;
import java.util.UUID;

import com.leanhduc.telegramclone.dto.message.PinMessageResult;

public interface IMessageService {
    ChatMessageResponse saveMessage(UUID senderId, ChatMessageRequest request);

    List<ChatMessageResponse> getMessageHistory(UUID conversationId, UUID currentUserId, Long cursor, int size);

    void markMessagesAsRead(UUID currentUserId, ChatReadRequest request);

    ChatMessageResponse editMessage(UUID currentUserId, Long messageId, EditMessageRequest request);

    UUID deleteMessage(UUID currentUserId, Long messageId);

    void incrementViews(UUID userId, UUID conversationId, List<Long> messageIds);

    PinMessageResult pinMessage(UUID userId, UUID conversationId, Long messageId);

    void unpinMessage(UUID userId, UUID conversationId, Long messageId);
}