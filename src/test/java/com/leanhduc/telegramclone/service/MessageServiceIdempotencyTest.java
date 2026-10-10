package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.event.MessageCreatedEvent;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceIdempotencyTest {

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

    private UUID senderId;
    private UUID partnerId;
    private UUID conversationId;
    private Conversation privateConversation;
    private ConversationMember senderMember;
    private User senderUser;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        privateConversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.PRIVATE)
                .build();

        senderUser = User.builder()
                .id(senderId)
                .username("sender")
                .displayName("Sender User")
                .build();

        senderMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversationId, senderId))
                .conversation(privateConversation)
                .user(senderUser)
                .role(ConversationRole.MEMBER)
                .build();

        ConversationMember partnerMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversationId, partnerId))
                .conversation(privateConversation)
                .user(User.builder().id(partnerId).username("partner").build())
                .role(ConversationRole.MEMBER)
                .build();

        lenient().when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(privateConversation));
        lenient().when(memberRepository.findById(new ConversationMemberId(conversationId, senderId))).thenReturn(Optional.of(senderMember));
        lenient().when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId)).thenReturn(List.of(senderMember, partnerMember));
        lenient().when(contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("Gửi tin nhắn lần đầu với clientMessageId -> Lưu DB thành công và bắn Event")
    void testSaveMessage_FirstTime_Success() {
        String clientMessageId = UUID.randomUUID().toString();
        ChatMessageRequest request = new ChatMessageRequest(conversationId, "Hello world", List.of(), null, clientMessageId);

        when(messageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId)).thenReturn(Optional.empty());
        when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));

        Message savedMessage = Message.builder()
                .id(100L)
                .conversation(privateConversation)
                .sender(senderUser)
                .body("Hello world")
                .clientMessageId(clientMessageId)
                .createdAt(Instant.now())
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        ChatMessageResponse mockResponse = new ChatMessageResponse(
                100L, conversationId, senderId, "Sender User", "Hello world",
                Instant.now(), List.of(), false, null, null, "TEXT", null,
                List.of(), null, null, clientMessageId
        );
        when(messageMapper.toResponse(eq(savedMessage), any(), any(), any())).thenReturn(mockResponse);

        ChatMessageResponse response = messageService.saveMessage(senderId, request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(clientMessageId, response.clientMessageId());
        verify(messageRepository, times(1)).save(any(Message.class));
        verify(eventPublisher, times(1)).publishEvent(any(MessageCreatedEvent.class));
    }

    @Test
    @DisplayName("Client retry với clientMessageId đã tồn tại -> Trả về tin cũ, không gọi save thêm lần nào và không publishEvent lại")
    void testSaveMessage_RetryDuplicateClientMessageId_ReturnsExistingMessageWithoutReSaving() {
        String clientMessageId = UUID.randomUUID().toString();
        ChatMessageRequest request = new ChatMessageRequest(conversationId, "Hello world", List.of(), null, clientMessageId);

        Message existingMessage = Message.builder()
                .id(100L)
                .conversation(privateConversation)
                .sender(senderUser)
                .body("Hello world")
                .clientMessageId(clientMessageId)
                .createdAt(Instant.now())
                .build();

        when(messageRepository.findBySenderIdAndClientMessageId(senderId, clientMessageId)).thenReturn(Optional.of(existingMessage));
        when(messageMediaRepository.findByMessageIdInWithMedia(List.of(100L))).thenReturn(List.of());

        ChatMessageResponse mockResponse = new ChatMessageResponse(
                100L, conversationId, senderId, "Sender User", "Hello world",
                Instant.now(), List.of(), false, null, null, "TEXT", null,
                List.of(), null, null, clientMessageId
        );
        when(messageMapper.toResponse(eq(existingMessage), any(), any(), any())).thenReturn(mockResponse);

        ChatMessageResponse response = messageService.saveMessage(senderId, request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(clientMessageId, response.clientMessageId());
        // Verify: tuyệt đối không tạo mới và không broadcast lại
        verify(messageRepository, never()).save(any(Message.class));
        verify(eventPublisher, never()).publishEvent(any(MessageCreatedEvent.class));
    }
}
