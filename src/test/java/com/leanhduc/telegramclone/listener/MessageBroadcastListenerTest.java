package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageBroadcastListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageBroadcastListener listener;

    @Test
    @DisplayName("Should broadcast MESSAGE_EDITED to individual users for group conversation")
    void handleMessageEdited_whenGroup_shouldSendToUsers() {
        UUID conversationId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        ChatMessageResponse response = new ChatMessageResponse(
                10L, conversationId, user1, "User1", "Updated body",
                Instant.now(), List.of(), true, null, null, "TEXT", null
        );

        MessageEditedEvent event = new MessageEditedEvent(
                response,
                ConversationType.GROUP,
                List.of(user1, user2)
        );

        listener.handleMessageEdited(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user1.toString()), eq("/queue/chat"), envelopeCaptor.capture());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user2.toString()), eq("/queue/chat"), any(WsEnvelope.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(WsEnvelope.class));

        WsEnvelope<ChatMessageResponse> captured = envelopeCaptor.getValue();
        assertEquals("MESSAGE_EDITED", captured.event());
        assertEquals("Updated body", captured.data().message());
    }

    @Test
    @DisplayName("Should broadcast MESSAGE_EDITED to topic for large channel with >1000 members")
    void handleMessageEdited_whenLargeChannel_shouldSendToTopic() {
        UUID channelId = UUID.randomUUID();
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < 1005; i++) {
            memberIds.add(UUID.randomUUID());
        }

        ChatMessageResponse response = new ChatMessageResponse(
                20L, channelId, UUID.randomUUID(), "Admin", "Edited channel post",
                Instant.now(), List.of(), true, null, 100L, "TEXT", null
        );

        MessageEditedEvent event = new MessageEditedEvent(
                response,
                ConversationType.CHANNEL,
                memberIds
        );

        listener.handleMessageEdited(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/channels/" + channelId), envelopeCaptor.capture());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(WsEnvelope.class));

        WsEnvelope<ChatMessageResponse> captured = envelopeCaptor.getValue();
        assertEquals("MESSAGE_EDITED", captured.event());
    }

    @Test
    @DisplayName("Should broadcast MESSAGE_DELETED to individual users for group conversation")
    void handleMessageDeleted_whenGroup_shouldSendToUsers() {
        UUID conversationId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Long messageId = 55L;

        MessageDeletedEvent event = new MessageDeletedEvent(
                messageId,
                conversationId,
                ConversationType.GROUP,
                List.of(user1, user2)
        );

        listener.handleMessageDeleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<DeleteMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user1.toString()), eq("/queue/chat"), envelopeCaptor.capture());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user2.toString()), eq("/queue/chat"), any(WsEnvelope.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(WsEnvelope.class));

        WsEnvelope<DeleteMessageResponse> captured = envelopeCaptor.getValue();
        assertEquals("MESSAGE_DELETED", captured.event());
        assertEquals(messageId, captured.data().messageId());
        assertEquals(conversationId, captured.data().conversationId());
    }

    @Test
    @DisplayName("Should broadcast MESSAGE_DELETED to topic for large channel")
    void handleMessageDeleted_whenLargeChannel_shouldSendToTopic() {
        UUID channelId = UUID.randomUUID();
        Long messageId = 99L;
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            memberIds.add(UUID.randomUUID());
        }

        MessageDeletedEvent event = new MessageDeletedEvent(
                messageId,
                channelId,
                ConversationType.CHANNEL,
                memberIds
        );

        listener.handleMessageDeleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<DeleteMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/channels/" + channelId), envelopeCaptor.capture());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(WsEnvelope.class));

        WsEnvelope<DeleteMessageResponse> captured = envelopeCaptor.getValue();
        assertEquals("MESSAGE_DELETED", captured.event());
        assertEquals(messageId, captured.data().messageId());
    }
}
