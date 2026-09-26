package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.DiscussionBroadcastEvent;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscussionBroadcastListenerTest {

    @Mock
    private ChatBroadcaster chatBroadcaster;

    @InjectMocks
    private DiscussionBroadcastListener listener;

    @Test
    @DisplayName("Should broadcast groupRootResponse and commentCountUpdate via ChatBroadcaster")
    void handleDiscussionBroadcast_shouldDelegateToChatBroadcaster() {
        UUID channelId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        ChatMessageResponse groupRoot = new ChatMessageResponse(
                100L, groupId, senderId, "Channel Post in Group", "Post body",
                Instant.now(), List.of(), false, null, null, "TEXT", null, List.of(), 0
        );

        CommentCountUpdateDto countUpdate = new CommentCountUpdateDto(
                50L, channelId, 100L, groupId, 5
        );

        DiscussionBroadcastEvent event = new DiscussionBroadcastEvent(
                groupRoot, List.of(), countUpdate, channelId, List.of()
        );

        listener.handleDiscussionBroadcast(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<ChatMessageResponse>> groupCaptor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(chatBroadcaster, times(1)).broadcast(eq(groupId), eq(ConversationType.GROUP), groupCaptor.capture());
        assertEquals("NEW_MESSAGE", groupCaptor.getValue().event());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WsEnvelope<CommentCountUpdateDto>> countCaptor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(chatBroadcaster, times(1)).broadcast(eq(channelId), eq(ConversationType.CHANNEL), countCaptor.capture());
        verify(chatBroadcaster, times(1)).broadcast(eq(groupId), eq(ConversationType.GROUP), countCaptor.capture());
        assertEquals("COMMENT_COUNT_UPDATED", countCaptor.getValue().event());
    }

    @Test
    @DisplayName("Should do nothing when event is null")
    void handleDiscussionBroadcast_whenNull_shouldDoNothing() {
        listener.handleDiscussionBroadcast(null);

        verifyNoInteractions(chatBroadcaster);
    }
}
