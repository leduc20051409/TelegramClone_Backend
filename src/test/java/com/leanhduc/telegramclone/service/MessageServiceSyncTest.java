package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.SyncMessagesResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import com.leanhduc.telegramclone.service.message.MessageService;
import com.leanhduc.telegramclone.service.message.discussion.IDiscussionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceSyncTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UnreadCounterRepository unreadCounterRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MessageMediaRepository messageMediaRepository;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private MessagePostViewRepository messagePostViewRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private PinnedMessageRepository pinnedMessageRepository;
    @Mock
    private DiscussionThreadLinkRepository discussionThreadLinkRepository;
    @Mock
    private IDiscussionService discussionService;
    @Mock
    private IPermissionService permissionService;
    @Mock
    private ContactRepository contactRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageService messageService;

    private UUID userId;
    private UUID conversationId;
    private Conversation conversation;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        conversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.GROUP)
                .build();

        user = User.builder()
                .id(userId)
                .username("testuser")
                .build();
    }

    @Test
    @DisplayName("Sync tin nhắn thành công: Trả về tin mới hơn afterId, hasMore=false")
    void testSyncMessages_Success() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)).thenReturn(true);

        Message m1 = Message.builder().id(51L).conversation(conversation).sender(user).body("Msg 51").createdAt(Instant.now()).build();
        Message m2 = Message.builder().id(52L).conversation(conversation).sender(user).body("Msg 52").createdAt(Instant.now()).build();

        when(messageRepository.findMessagesAfterId(eq(conversationId), eq(50L), eq(PageRequest.of(0, 101))))
                .thenReturn(List.of(m1, m2));
        when(messageMediaRepository.findByMessageIdInWithMedia(any())).thenReturn(List.of());

        ChatMessageResponse r1 = new ChatMessageResponse(51L, conversationId, userId, "testuser", "Msg 51", Instant.now(), List.of(), false, null, null, "TEXT", null);
        ChatMessageResponse r2 = new ChatMessageResponse(52L, conversationId, userId, "testuser", "Msg 52", Instant.now(), List.of(), false, null, null, "TEXT", null);

        when(messageMapper.toResponse(eq(m1), any(), any(), any())).thenReturn(r1);
        when(messageMapper.toResponse(eq(m2), any(), any(), any())).thenReturn(r2);

        SyncMessagesResponse response = messageService.syncMessages(conversationId, userId, 50L, 100);

        assertNotNull(response);
        assertEquals(2, response.messages().size());
        assertFalse(response.hasMore());
        assertEquals(52L, response.nextAfterId());
    }

    @Test
    @DisplayName("Sync tin nhắn với số lượng > limit: hasMore=true, nextAfterId là id của phần tử thứ 100")
    void testSyncMessages_HasMoreTrue() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)).thenReturn(true);

        List<Message> mockList = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            mockList.add(Message.builder().id(i).conversation(conversation).sender(user).body("Msg " + i).createdAt(Instant.now()).build());
        }

        when(messageRepository.findMessagesAfterId(eq(conversationId), eq(0L), eq(PageRequest.of(0, 101))))
                .thenReturn(mockList);
        when(messageMediaRepository.findByMessageIdInWithMedia(any())).thenReturn(List.of());

        ChatMessageResponse dummyResp = new ChatMessageResponse(1L, conversationId, userId, "testuser", "Msg", Instant.now(), List.of(), false, null, null, "TEXT", null);
        when(messageMapper.toResponse(any(Message.class), any(), any(), any())).thenReturn(dummyResp);

        SyncMessagesResponse response = messageService.syncMessages(conversationId, userId, 0L, 100);

        assertNotNull(response);
        assertEquals(100, response.messages().size());
        assertTrue(response.hasMore());
        assertEquals(100L, response.nextAfterId());
    }

    @Test
    @DisplayName("User không thuộc conversation -> Ném NOT_IN_CONVERSATION")
    void testSyncMessages_NotInConversation_ThrowsException() {
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                messageService.syncMessages(conversationId, userId, 0L, 100)
        );

        assertEquals(ErrorCode.NOT_IN_CONVERSATION, ex.getErrorCode());
        verify(messageRepository, never()).findMessagesAfterId(any(), any(), any());
    }
}
