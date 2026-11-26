package com.leanhduc.telegramclone.listener.broadcast;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.ChannelPostMirroredEvent;
import com.leanhduc.telegramclone.event.DiscussionCommentCountUpdatedEvent;
import com.leanhduc.telegramclone.event.MessageCreatedEvent;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
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
    @DisplayName("Should broadcast NEW_MESSAGE for each target in MessagesForwardedEvent")
    void handleMessagesForwarded_shouldDelegateToChatBroadcaster() {
        UUID conv1 = UUID.randomUUID();
        UUID conv2 = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessageResponse resp1 = new ChatMessageResponse(
                10L, conv1, senderId, "User1", "Forwarded 1",
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );
        ChatMessageResponse resp2 = new ChatMessageResponse(
                11L, conv2, senderId, "User1", "Forwarded 2",
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );

        MessagesForwardedEvent event = new MessagesForwardedEvent(List.of(
                new MessagesForwardedEvent.TargetBroadcastDto(conv1, ConversationType.GROUP, resp1),
                new MessagesForwardedEvent.TargetBroadcastDto(conv2, ConversationType.CHANNEL, resp2)
        ));

        listener.handleMessagesForwarded(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> envelopeCaptor = ArgumentCaptor.forClass(WsEnvelope.class);

        verify(chatBroadcaster, times(1)).broadcast(eq(conv1), eq(ConversationType.GROUP), envelopeCaptor.capture());
        verify(chatBroadcaster, times(1)).broadcast(eq(conv2), eq(ConversationType.CHANNEL), envelopeCaptor.capture());

        List<WsEnvelope<ChatMessageResponse>> captured = envelopeCaptor.getAllValues();
        assertEquals(2, captured.size());
        assertEquals("NEW_MESSAGE", captured.get(0).event());
        assertEquals("Forwarded 1", captured.get(0).data().message());
        assertEquals("NEW_MESSAGE", captured.get(1).event());
        assertEquals("Forwarded 2", captured.get(1).data().message());
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

        MessageEditedEvent event = new MessageEditedEvent(response, ConversationType.GROUP);

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
                ConversationType.CHANNEL
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
    @DisplayName("Should broadcast NEW_MESSAGE and COMMENT_COUNT_UPDATED for discussion events")
    void handleDiscussionEvents_shouldDelegateToChatBroadcaster() {
        UUID channelId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessageResponse groupRoot = new ChatMessageResponse(
                100L, groupId, senderId, "Channel Post in Group", "Post body",
                Instant.now(), List.of(), false, null, null, "TEXT", null, List.of(), 0
        );
        CommentCountUpdateDto countUpdate = new CommentCountUpdateDto(50L, channelId, 100L, groupId, 5);

        listener.handleChannelPostMirrored(new ChannelPostMirroredEvent(groupRoot));
        listener.handleDiscussionCommentCountUpdated(new DiscussionCommentCountUpdatedEvent(channelId, countUpdate));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<?>> groupCaptor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(chatBroadcaster, times(2)).broadcast(eq(groupId), eq(ConversationType.GROUP), groupCaptor.capture());
        List<WsEnvelope<?>> groupEnvelopes = groupCaptor.getAllValues();
        assertEquals("NEW_MESSAGE", groupEnvelopes.get(0).event());
        assertEquals("COMMENT_COUNT_UPDATED", groupEnvelopes.get(1).event());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<CommentCountUpdateDto>> channelCaptor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(chatBroadcaster, times(1)).broadcast(eq(channelId), eq(ConversationType.CHANNEL), channelCaptor.capture());
        assertEquals("COMMENT_COUNT_UPDATED", channelCaptor.getValue().event());
    }

    @Test
    @DisplayName("Should do nothing when events are null or contain null data")
    void handleEvents_whenNull_shouldDoNothing() {
        listener.handleMessageCreated(null);
        listener.handleMessageCreated(new MessageCreatedEvent(null, ConversationType.GROUP));
        listener.handleMessagesForwarded(null);
        listener.handleMessagesForwarded(new MessagesForwardedEvent(null));
        listener.handleMessagesRead(null);
        listener.handleMessagesRead(new MessagesReadEvent(null, ConversationType.GROUP, null, 1L));
        listener.handleMessageEdited(null);
        listener.handleMessageEdited(new MessageEditedEvent(null, ConversationType.GROUP));
        listener.handleMessageDeleted(null);
        listener.handleMessageDeleted(new MessageDeletedEvent(null, null, ConversationType.GROUP));
        listener.handleChannelPostMirrored(null);
        listener.handleChannelPostMirrored(new ChannelPostMirroredEvent(null));
        listener.handleDiscussionCommentCountUpdated(null);
        listener.handleDiscussionCommentCountUpdated(new DiscussionCommentCountUpdatedEvent(null, null));

        verifyNoInteractions(chatBroadcaster);
    }
}
