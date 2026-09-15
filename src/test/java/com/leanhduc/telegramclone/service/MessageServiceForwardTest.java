package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ForwardMessageRequest;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import com.leanhduc.telegramclone.model.enums.MessageType;
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
class MessageServiceForwardTest {

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

    private UUID currentUserId;
    private User currentUser;
    private Long sourceMessageId;
    private Message sourceMessage;
    private Conversation sourceConversation;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        currentUser = User.builder().id(currentUserId).username("current_user").build();

        sourceConversation = Conversation.builder()
                .id(UUID.randomUUID())
                .type(ConversationType.GROUP)
                .title("Source Group")
                .build();

        sourceMessageId = 100L;
        sourceMessage = Message.builder()
                .id(sourceMessageId)
                .conversation(sourceConversation)
                .sender(User.builder().id(UUID.randomUUID()).username("original_author").build())
                .body("Original text")
                .messageType(MessageType.TEXT)
                .deleted(false)
                .build();
    }

    @Test
    @DisplayName("Should throw MESSAGE_NOT_FOUND when source message does not exist")
    void forwardMessage_whenSourceNotFound_shouldThrowMessageNotFound() {
        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.empty());

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw MESSAGE_NOT_FOUND when source message is soft-deleted")
    void forwardMessage_whenSourceDeleted_shouldThrowMessageNotFound() {
        sourceMessage.setDeleted(true);
        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.MESSAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw CANNOT_FORWARD_MESSAGE_TYPE when source message is SYSTEM type")
    void forwardMessage_whenSourceIsSystemType_shouldThrowCannotForwardMessageType() {
        sourceMessage.setMessageType(MessageType.SYSTEM);
        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.CANNOT_FORWARD_MESSAGE_TYPE, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw NOT_IN_CONVERSATION when current user is not a member of source conversation")
    void forwardMessage_whenUserNotMemberOfSource_shouldThrowNotInConversation() {
        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(sourceConversation.getId(), currentUserId))
                .thenReturn(false);

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.NOT_IN_CONVERSATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw DUPLICATE_TARGET_CONVERSATION when target list contains duplicates")
    void forwardMessage_whenTargetListHasDuplicates_shouldThrowDuplicateTargetConversation() {
        UUID targetId = UUID.randomUUID();
        ForwardMessageRequest request = new ForwardMessageRequest(List.of(targetId, targetId));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.DUPLICATE_TARGET_CONVERSATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("All-or-nothing: should reject entire operation and not save any message if one target fails permission")
    void forwardMessage_allOrNothing_whenOneTargetFailsPermission_shouldRollbackAndThrowPermissionDenied() {
        UUID target1 = UUID.randomUUID();
        UUID target2 = UUID.randomUUID();

        Conversation conv1 = Conversation.builder().id(target1).type(ConversationType.GROUP).build();
        Conversation conv2 = Conversation.builder().id(target2).type(ConversationType.GROUP).build();

        ConversationMember member1 = ConversationMember.builder()
                .id(new ConversationMemberId(target1, currentUserId))
                .conversation(conv1)
                .user(currentUser)
                .build();
        ConversationMember member2 = ConversationMember.builder()
                .id(new ConversationMemberId(target2, currentUserId))
                .conversation(conv2)
                .user(currentUser)
                .build();

        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(sourceConversation.getId(), currentUserId))
                .thenReturn(true);

        when(conversationRepository.findById(target1)).thenReturn(Optional.of(conv1));
        when(memberRepository.findById(new ConversationMemberId(target1, currentUserId))).thenReturn(Optional.of(member1));
        when(permissionService.hasMemberPermission(member1, MemberPermission.SEND_MESSAGES)).thenReturn(true);
        when(memberRepository.findByConversationIdAndLeftAtIsNull(target1)).thenReturn(List.of(member1));

        // Target 2 fails permission
        when(conversationRepository.findById(target2)).thenReturn(Optional.of(conv2));
        when(memberRepository.findById(new ConversationMemberId(target2, currentUserId))).thenReturn(Optional.of(member2));
        when(permissionService.hasMemberPermission(member2, MemberPermission.SEND_MESSAGES)).thenReturn(false);

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(target1, target2));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                messageService.forwardMessage(currentUserId, sourceMessageId, request)
        );

        assertEquals(ErrorCode.PERMISSION_DENIED, exception.getErrorCode());
        // Verify NO messages were created or saved
        verify(messageRepository, never()).save(any(Message.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should preserve original attribution and publish AFTER_COMMIT event when forwarding succeeds")
    void forwardMessage_whenSuccessful_shouldPreserveAttributionAndPublishEvent() {
        UUID targetId = UUID.randomUUID();
        Conversation targetConv = Conversation.builder().id(targetId).type(ConversationType.GROUP).build();
        ConversationMember targetMember = ConversationMember.builder()
                .id(new ConversationMemberId(targetId, currentUserId))
                .conversation(targetConv)
                .user(currentUser)
                .build();

        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(sourceConversation.getId(), currentUserId))
                .thenReturn(true);

        when(conversationRepository.findById(targetId)).thenReturn(Optional.of(targetConv));
        when(memberRepository.findById(new ConversationMemberId(targetId, currentUserId))).thenReturn(Optional.of(targetMember));
        when(permissionService.hasMemberPermission(targetMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);
        when(memberRepository.findByConversationIdAndLeftAtIsNull(targetId)).thenReturn(List.of(targetMember));

        Message savedMsg = Message.builder()
                .id(200L)
                .conversation(targetConv)
                .sender(currentUser)
                .body(sourceMessage.getBody())
                .messageType(sourceMessage.getMessageType())
                .forwardedFromUser(sourceMessage.getSender())
                .forwardedFromConversation(sourceConversation)
                .forwardedAt(Instant.now())
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                200L, targetId, currentUserId, currentUser.getUsername(), sourceMessage.getBody(),
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );
        when(messageMapper.toResponse(any(Message.class), anyList(), isNull(), isNull())).thenReturn(expectedResponse);

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(targetId));
        List<ChatMessageResponse> results = messageService.forwardMessage(currentUserId, sourceMessageId, request);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(200L, results.get(0).id());

        // Verify event was published for AFTER_COMMIT handling
        verify(eventPublisher, times(1)).publishEvent(any(MessagesForwardedEvent.class));
    }

    @Test
    @DisplayName("Should preserve original creator when forwarding a message that was already forwarded")
    void forwardMessage_whenSourceAlreadyForwarded_shouldPreserveOriginalSourceAttribution() {
        User originalCreator = User.builder().id(UUID.randomUUID()).username("original_creator").build();
        Conversation originalChannel = Conversation.builder().id(UUID.randomUUID()).title("Original Channel").build();

        sourceMessage.setForwardedFromUser(originalCreator);
        sourceMessage.setForwardedFromConversation(originalChannel);
        sourceMessage.setForwardedAt(Instant.now().minusSeconds(3600));

        UUID targetId = UUID.randomUUID();
        Conversation targetConv = Conversation.builder().id(targetId).type(ConversationType.GROUP).build();
        ConversationMember targetMember = ConversationMember.builder()
                .id(new ConversationMemberId(targetId, currentUserId))
                .conversation(targetConv)
                .user(currentUser)
                .build();

        when(messageRepository.findById(sourceMessageId)).thenReturn(Optional.of(sourceMessage));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(sourceConversation.getId(), currentUserId))
                .thenReturn(true);

        when(conversationRepository.findById(targetId)).thenReturn(Optional.of(targetConv));
        when(memberRepository.findById(new ConversationMemberId(targetId, currentUserId))).thenReturn(Optional.of(targetMember));
        when(permissionService.hasMemberPermission(targetMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);
        when(memberRepository.findByConversationIdAndLeftAtIsNull(targetId)).thenReturn(List.of(targetMember));

        when(messageRepository.save(argThat(msg ->
                msg.getForwardedFromUser().equals(originalCreator) &&
                msg.getForwardedFromConversation().equals(originalChannel)
        ))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                300L, targetId, currentUserId, currentUser.getUsername(), sourceMessage.getBody(),
                Instant.now(), List.of(), false, null, null, "TEXT", null
        );
        when(messageMapper.toResponse(any(Message.class), anyList(), isNull(), isNull())).thenReturn(expectedResponse);

        ForwardMessageRequest request = new ForwardMessageRequest(List.of(targetId));
        List<ChatMessageResponse> results = messageService.forwardMessage(currentUserId, sourceMessageId, request);

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(messageRepository, times(1)).save(any(Message.class));
    }
}
