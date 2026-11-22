package com.leanhduc.telegramclone.service.broadcast;

import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompChatBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ConversationMemberRepository memberRepository;

    @InjectMocks
    private StompChatBroadcaster broadcaster;

    @Test
    @DisplayName("When channel count > 1000, should broadcast to topic and not load members")
    void broadcast_whenLargeChannel_shouldSendToTopic() {
        UUID channelId = UUID.randomUUID();
        WsEnvelope<String> envelope = WsEnvelope.of("TEST_EVENT", "Hello");

        when(memberRepository.countByConversationIdAndLeftAtIsNull(channelId)).thenReturn(1005L);

        broadcaster.broadcast(channelId, ConversationType.CHANNEL, envelope);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/channels/" + channelId), eq(envelope));
        verify(memberRepository, never()).findActiveUserIdsByConversationId(any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("When channel count <= 1000, should broadcast to user queues")
    void broadcast_whenSmallChannel_shouldSendToUserQueues() {
        UUID channelId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        WsEnvelope<String> envelope = WsEnvelope.of("TEST_EVENT", "Hello");

        when(memberRepository.countByConversationIdAndLeftAtIsNull(channelId)).thenReturn(2L);
        when(memberRepository.findActiveUserIdsByConversationId(channelId)).thenReturn(List.of(user1, user2));

        broadcaster.broadcast(channelId, ConversationType.CHANNEL, envelope);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user1.toString()), eq("/queue/chat"), eq(envelope));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user2.toString()), eq("/queue/chat"), eq(envelope));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(WsEnvelope.class));
    }

    @Test
    @DisplayName("When group conversation, should broadcast to all active members without checking count")
    void broadcast_whenGroup_shouldSendToAllMembers() {
        UUID groupId = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        WsEnvelope<String> envelope = WsEnvelope.of("TEST_EVENT", "Group update");

        when(memberRepository.findActiveUserIdsByConversationId(groupId)).thenReturn(List.of(user1, user2));

        broadcaster.broadcast(groupId, ConversationType.GROUP, envelope);

        verify(memberRepository, never()).countByConversationIdAndLeftAtIsNull(any());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user1.toString()), eq("/queue/chat"), eq(envelope));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(user2.toString()), eq("/queue/chat"), eq(envelope));
    }

    @Test
    @DisplayName("When broadcastExcept is called, should skip excluded user")
    void broadcastExcept_whenExcludedUser_shouldSkipThatUser() {
        UUID convId = UUID.randomUUID();
        UUID readerId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        WsEnvelope<String> envelope = WsEnvelope.of("MESSAGES_READ", "read-receipt");

        when(memberRepository.findActiveUserIdsByConversationId(convId)).thenReturn(List.of(readerId, otherUser));

        broadcaster.broadcastExcept(convId, ConversationType.GROUP, readerId, envelope);

        verify(messagingTemplate, never()).convertAndSendToUser(eq(readerId.toString()), anyString(), any());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(otherUser.toString()), eq("/queue/chat"), eq(envelope));
    }

    @Test
    @DisplayName("When broadcastIncluding is called, should include additional user without duplicate")
    void broadcastIncluding_whenAdditionalUser_shouldDeliverOnceToEachRecipient() {
        UUID convId = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        UUID leftUser = UUID.randomUUID();
        WsEnvelope<String> envelope = WsEnvelope.of("MEMBER_LEFT", "left");

        when(memberRepository.findActiveUserIdsByConversationId(convId)).thenReturn(List.of(activeUser));

        broadcaster.broadcastIncluding(convId, ConversationType.GROUP, leftUser, envelope);

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(activeUser.toString()), eq("/queue/chat"), eq(envelope));
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(leftUser.toString()), eq("/queue/chat"), eq(envelope));
    }

    @Test
    @DisplayName("When conversationId or envelope is null, should do nothing")
    void broadcast_whenNullInputs_shouldDoNothing() {
        broadcaster.broadcast(null, ConversationType.GROUP, WsEnvelope.of("TEST", "data"));
        broadcaster.broadcast(UUID.randomUUID(), ConversationType.GROUP, null);

        verifyNoInteractions(memberRepository);
        verifyNoInteractions(messagingTemplate);
    }
}
