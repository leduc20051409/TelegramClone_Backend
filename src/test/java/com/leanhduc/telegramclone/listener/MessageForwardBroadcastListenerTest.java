package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
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
class MessageForwardBroadcastListenerTest {

    @Mock
    private ChatBroadcaster chatBroadcaster;

    @InjectMocks
    private MessageForwardBroadcastListener listener;

    @Test
    @DisplayName("Should broadcast NEW_MESSAGE to chat broadcaster for each target in forwarded event")
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

        MessagesForwardedEvent.TargetBroadcastDto b1 = new MessagesForwardedEvent.TargetBroadcastDto(
                conv1, ConversationType.GROUP, List.of(senderId), resp1
        );
        MessagesForwardedEvent.TargetBroadcastDto b2 = new MessagesForwardedEvent.TargetBroadcastDto(
                conv2, ConversationType.CHANNEL, List.of(), resp2
        );

        MessagesForwardedEvent event = new MessagesForwardedEvent(List.of(b1, b2));

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
    @DisplayName("Should do nothing when event or broadcasts is null")
    void handleMessagesForwarded_whenNull_shouldDoNothing() {
        listener.handleMessagesForwarded(null);
        listener.handleMessagesForwarded(new MessagesForwardedEvent(null));

        verifyNoInteractions(chatBroadcaster);
    }
}
