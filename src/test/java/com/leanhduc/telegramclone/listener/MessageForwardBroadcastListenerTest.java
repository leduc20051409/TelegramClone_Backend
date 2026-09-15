package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageForwardBroadcastListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageForwardBroadcastListener listener;

    @Test
    @DisplayName("Should broadcast NEW_MESSAGE to individual users for group or small channel")
    void handleMessagesForwarded_whenGroup_shouldSendToUsers() {
        UUID conversationId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        ChatMessageResponse response = new ChatMessageResponse(
                10L, conversationId, user1, "User1", "Forwarded body",
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );

        MessagesForwardedEvent.TargetBroadcastDto broadcast = new MessagesForwardedEvent.TargetBroadcastDto(
                conversationId,
                ConversationType.GROUP,
                List.of(user1, user2),
                response
        );

        MessagesForwardedEvent event = new MessagesForwardedEvent(List.of(broadcast));

        listener.handleMessagesForwarded(event);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user1.toString()), eq("/queue/chat"), any(WsEnvelope.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user2.toString()), eq("/queue/chat"), any(WsEnvelope.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(WsEnvelope.class));
    }

    @Test
    @DisplayName("Should broadcast to topic for large channel with >1000 members")
    void handleMessagesForwarded_whenLargeChannel_shouldSendToTopic() {
        UUID channelId = UUID.randomUUID();
        List<UUID> memberIds = new java.util.ArrayList<>();
        for (int i = 0; i < 1005; i++) {
            memberIds.add(UUID.randomUUID());
        }

        ChatMessageResponse response = new ChatMessageResponse(
                20L, channelId, UUID.randomUUID(), "Admin", "Channel announcement",
                Instant.now(), List.of(), false, null, 0L, "TEXT", null
        );

        MessagesForwardedEvent.TargetBroadcastDto broadcast = new MessagesForwardedEvent.TargetBroadcastDto(
                channelId,
                ConversationType.CHANNEL,
                memberIds,
                response
        );

        MessagesForwardedEvent event = new MessagesForwardedEvent(List.of(broadcast));

        listener.handleMessagesForwarded(event);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/channels/" + channelId), any(WsEnvelope.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(WsEnvelope.class));
    }
}
