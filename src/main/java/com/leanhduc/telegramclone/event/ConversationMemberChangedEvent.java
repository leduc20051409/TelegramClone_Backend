package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.MemberEventResponse;
import com.leanhduc.telegramclone.model.enums.ConversationType;

import java.util.UUID;

public record ConversationMemberChangedEvent(
        UUID conversationId,
        ConversationType conversationType,
        String eventType,
        MemberEventResponse memberData,
        ChatMessageResponse systemMessage,
        UUID additionalRecipientId
) {}
