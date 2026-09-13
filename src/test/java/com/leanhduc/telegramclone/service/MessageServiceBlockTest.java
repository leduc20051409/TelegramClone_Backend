package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceBlockTest {

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

    @InjectMocks
    private MessageService messageService;

    private UUID senderId;
    private UUID partnerId;
    private UUID conversationId;
    private Conversation privateConversation;
    private ConversationMember senderMember;
    private ConversationMember partnerMember;
    private ChatMessageRequest chatRequest;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        privateConversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.PRIVATE)
                .build();

        User sender = User.builder().id(senderId).username("sender").build();
        User partner = User.builder().id(partnerId).username("partner").build();

        senderMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversationId, senderId))
                .conversation(privateConversation)
                .user(sender)
                .role(ConversationRole.MEMBER)
                .build();

        partnerMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversationId, partnerId))
                .conversation(privateConversation)
                .user(partner)
                .role(ConversationRole.MEMBER)
                .build();

        chatRequest = new ChatMessageRequest(conversationId, "Hello", null, null);
    }

    @Test
    @DisplayName("Should throw USER_BLOCKED when partner blocked the sender in private chat")
    void saveMessage_whenPartnerBlockedSenderInPrivateChat_shouldThrowUserBlocked() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(privateConversation));
        when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                .thenReturn(Optional.of(senderMember));
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(senderMember, partnerMember));

        // Partner has blocked sender
        when(contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(partnerId, senderId))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.saveMessage(senderId, chatRequest)
        );

        assertEquals(ErrorCode.USER_BLOCKED, exception.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CANNOT_MESSAGE_BLOCKED_USER when sender has blocked the partner in private chat")
    void saveMessage_whenSenderBlockedPartnerInPrivateChat_shouldThrowCannotMessageBlockedUser() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(privateConversation));
        when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                .thenReturn(Optional.of(senderMember));
        when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId))
                .thenReturn(List.of(senderMember, partnerMember));

        // Partner did not block sender, but sender blocked partner
        when(contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(partnerId, senderId))
                .thenReturn(false);
        when(contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(senderId, partnerId))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.saveMessage(senderId, chatRequest)
        );

        assertEquals(ErrorCode.CANNOT_MESSAGE_BLOCKED_USER, exception.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow message sending in group even if members blocked each other")
    void saveMessage_whenGroupChat_shouldNotCheckDirectBlock() {
        Conversation groupConversation = Conversation.builder()
                .id(conversationId)
                .type(ConversationType.GROUP)
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
        when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                .thenReturn(Optional.of(senderMember));
        when(permissionService.hasMemberPermission(senderMember, MemberPermission.SEND_MESSAGES))
                .thenReturn(true);

        User sender = senderMember.getUser();
        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));

        Message savedMessage = Message.builder()
                .id(100L)
                .conversation(groupConversation)
                .sender(sender)
                .body("Hello group")
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                100L, conversationId, senderId, sender.getUsername(), "Hello group",
                java.time.Instant.now(), List.of(), false, null, null, "TEXT", null
        );
        when(messageMapper.toResponse(any(Message.class), anyList(), isNull(), isNull()))
                .thenReturn(expectedResponse);

        ChatMessageResponse response = messageService.saveMessage(senderId, chatRequest);

        assertNotNull(response);
        assertEquals(100L, response.id());
        // Verify contactRepository was NEVER checked for block in group chat
        verify(contactRepository, never()).existsByOwnerIdAndContactIdAndIsBlockedTrue(any(), any());
        verify(messageRepository, times(1)).save(any(Message.class));
    }
}
