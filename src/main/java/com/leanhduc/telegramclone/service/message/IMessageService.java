package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;

import java.util.List;
import java.util.UUID;

public interface IMessageService {
    ChatMessageResponse saveMessage(UUID senderId, ChatMessageRequest request);

    List<ChatMessageResponse> getMessageHistory(UUID conversationId, UUID currentUserId, Long cursor, int size);
}