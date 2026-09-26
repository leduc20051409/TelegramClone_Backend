package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.MessageCreatedEvent;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.event.MessagesReadEvent;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.service.broadcast.ChatBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageBroadcastListenerTest {

    @Mock
    private ChatBroadcaster chatBroadcaster;

    @InjectMocks
    private MessageBroadcastListener listener;

    @Test
    @DisplayName("Should broadcast NEW_MESSAGE when MessageCreatedEvent is received")
    void handleMessageCreated_shouldDelegateToChatBroadcaster() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessageResponse response = new ChatMessageResponse(
                1L, conversationId, senderId, "Sender", "Hello World",
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );

        MessageCreatedEvent event = new MessageCreatedEvent(response, ConversationType.GROUP);

        listener.handleMessageCreated(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(chatBroadcaster, times(1)).broadcast(
                eq(conversationId),
                eq(ConversationType.GROUP),
                envelopeCaptor.capture()
        );

        WsEnvelope<ChatMessageResponse> captured = envelopeCaptor.getValue();
        assertNotNull(captured);
        assertEquals("NEW_MESSAGE", captured.event());
        assertEquals("Hello World", captured.data().message());
    }

    @Test
    @DisplayName("Should broadcast MESSAGES_READ except reader when MessagesReadEvent is received")
    void handleMessagesRead_shouldDelegateToChatBroadcasterExceptReader() {
        UUID conversationId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        Long lastReadMessageId = 42L;

        MessagesReadEvent event = new MessagesReadEvent(
                conversationId,
                ConversationType.GROUP,
                readerId,
                lastReadMessageId
        );

        listener.handleMessagesRead(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatReadRequest>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(chatBroadcaster, times(1)).broadcastExcept(
                eq(conversationId),
                eq(ConversationType.GROUP),
                eq(readerId),
                envelopeCaptor.capture()
        );

        WsEnvelope<ChatReadRequest> captured = envelopeCaptor.getValue();
        assertNotNull(captured);
        assertEquals("MESSAGES_READ", captured.event());
        assertEquals(conversationId, captured.data().conversationId());
        assertEquals(lastReadMessageId, captured.data().lastReadMessageId());
    }

    @Test
    @DisplayName("Should broadcast MESSAGE_EDITED when MessageEditedEvent is received")
    void handleMessageEdited_shouldDelegateToChatBroadcaster() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessageResponse response = new ChatMessageResponse(
                10L, conversationId, senderId, "User1", "Updated body",
                Instant.now(), List.of(), true, null, null, "TEXT", null
        );

        MessageEditedEvent event = new MessageEditedEvent(
                response,
                ConversationType.GROUP,
                List.of(senderId)
        );

        listener.handleMessageEdited(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(chatBroadcaster, times(1)).broadcast(
                eq(conversationId),
                eq(ConversationType.GROUP),
                envelopeCaptor.capture()
        );

        WsEnvelope<ChatMessageResponse> captured = envelopeCaptor.getValue();
        assertNotNull(captured);
        assertEquals("MESSAGE_EDITED", captured.event());
        assertEquals("Updated body", captured.data().message());
    }

    @Test
    @DisplayName("Should broadcast MESSAGE_DELETED when MessageDeletedEvent is received")
    void handleMessageDeleted_shouldDelegateToChatBroadcaster() {
        UUID conversationId = UUID.randomUUID();
        Long messageId = 55L;

        MessageDeletedEvent event = new MessageDeletedEvent(
                messageId,
                conversationId,
                ConversationType.CHANNEL,
                List.of()
        );

        listener.handleMessageDeleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<DeleteMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(chatBroadcaster, times(1)).broadcast(
                eq(conversationId),
                eq(ConversationType.CHANNEL),
                envelopeCaptor.capture()
        );

        WsEnvelope<DeleteMessageResponse> captured = envelopeCaptor.getValue();
        assertNotNull(captured);
        assertEquals("MESSAGE_DELETED", captured.event());
        assertEquals(messageId, captured.data().messageId());
        assertEquals(conversationId, captured.data().conversationId());
    }

    @Test
    @DisplayName("Should do nothing when events are null or contain null data")
    void handleEvents_whenNull_shouldDoNothing() {
        listener.handleMessageCreated(null);
        listener.handleMessageCreated(new MessageCreatedEvent(null, ConversationType.GROUP));
        listener.handleMessagesRead(null);
        listener.handleMessagesRead(new MessagesReadEvent(null, ConversationType.GROUP, null, 1L));
        listener.handleMessageEdited(null);
        listener.handleMessageEdited(new MessageEditedEvent(null, ConversationType.GROUP, List.of()));
        listener.handleMessageDeleted(null);
        listener.handleMessageDeleted(new MessageDeletedEvent(null, null, ConversationType.GROUP, List.of()));

        verifyNoInteractions(chatBroadcaster);
    }
}
