package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.EditMessageRequest;
import com.leanhduc.telegramclone.event.MediaCloudDeleteEvent;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.model.MessageMedia;
import com.leanhduc.telegramclone.model.MessageMediaId;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import com.leanhduc.telegramclone.service.message.MessageService;
import com.leanhduc.telegramclone.service.message.discussion.IDiscussionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@SuppressWarnings("unused")
class MessageServiceEditDeleteTest {

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
    private Conversation conversation;
    private Message message;
    private Long messageId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        currentUser = User.builder().id(currentUserId).username("sender").build();

        conversation = Conversation.builder()
                .id(UUID.randomUUID())
                .type(ConversationType.GROUP)
                .title("Test Group")
                .build();

        messageId = 123L;
        message = Message.builder()
                .id(messageId)
                .sender(currentUser)
                .conversation(conversation)
                .messageType(MessageType.TEXT)
                .body("Initial message")
                .deleted(false)
                .build();
    }

    @Test
    @DisplayName("Should edit message, save, and publish MessageEditedEvent")
    void editMessage_shouldPublishMessageEditedEvent() {
        EditMessageRequest request = new EditMessageRequest("Updated content", null);
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).username("receiver").build();

        ConversationMember member1 = ConversationMember.builder().user(currentUser).conversation(conversation).build();
        ConversationMember member2 = ConversationMember.builder().user(otherUser).conversation(conversation).build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageMediaRepository.findByMessageIdInWithMedia(anyList())).thenReturn(List.of());

        ChatMessageResponse expectedResponse = new ChatMessageResponse(
                messageId, conversation.getId(), currentUserId, "sender", "Updated content",
                Instant.now(), List.of(), true, null, null, "TEXT", null
        );
        when(messageMapper.toResponse(any(Message.class), eq(List.of()), isNull())).thenReturn(expectedResponse);

        ChatMessageResponse actualResponse = messageService.editMessage(currentUserId, messageId, request);

        assertEquals("Updated content", actualResponse.message());
        assertTrue(actualResponse.edited());

        ArgumentCaptor<MessageEditedEvent> eventCaptor = ArgumentCaptor.forClass(MessageEditedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        MessageEditedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(expectedResponse, publishedEvent.updatedMessage());
        assertEquals(ConversationType.GROUP, publishedEvent.conversationType());
    }

    @Test
    @DisplayName("Should soft delete message, save, and publish MessageDeletedEvent")
    void deleteMessage_shouldPublishMessageDeletedEvent() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID deletedConvId = messageService.deleteMessage(currentUserId, messageId);

        assertEquals(conversation.getId(), deletedConvId);
        assertTrue(message.isDeleted());

        ArgumentCaptor<MessageDeletedEvent> eventCaptor = ArgumentCaptor.forClass(MessageDeletedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        MessageDeletedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(messageId, publishedEvent.messageId());
        assertEquals(conversation.getId(), publishedEvent.conversationId());
        assertEquals(ConversationType.GROUP, publishedEvent.conversationType());
    }

    @Test
    @DisplayName("Should return conversationId and not publish event if message was already deleted")
    void deleteMessage_whenAlreadyDeleted_shouldNotPublishEvent() {
        message.setDeleted(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        UUID result = messageService.deleteMessage(currentUserId, messageId);

        assertEquals(conversation.getId(), result);
        verify(messageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should delete orphaned video from DB and publish MediaCloudDeleteEvent when deleting message")
    void deleteMessage_withOrphanedVideo_shouldPublishCloudDeleteEvent() {
        UUID mediaId = UUID.randomUUID();
        Media video = Media.builder()
                .id(mediaId)
                .storageKey("telegram-clone/user/uuid_clip")
                .resourceType("video")
                .mimeType("video/mp4")
                .build();
        MessageMedia mm = MessageMedia.builder()
                .id(new MessageMediaId(messageId, mediaId))
                .message(message)
                .media(video)
                .ordinal(0)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageMediaRepository.findByMessageIdInWithMedia(List.of(messageId))).thenReturn(List.of(mm));
        when(messageMediaRepository.existsByMediaId(mediaId)).thenReturn(false);
        when(userRepository.existsByAvatarMediaId(mediaId)).thenReturn(false);
        when(conversationRepository.existsByAvatarMediaId(mediaId)).thenReturn(false);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.deleteMessage(currentUserId, messageId);

        assertEquals(MediaStatus.PENDING_DELETE, video.getStatus());
        verify(messageMediaRepository).deleteByMessageId(messageId);
        verify(mediaRepository).saveAll(List.of(video));
        verify(mediaRepository, never()).deleteAll(any());
        verify(eventPublisher).publishEvent(argThat((Object evt) ->
                evt instanceof MediaCloudDeleteEvent cloudEvt
                        && cloudEvt.targets().size() == 1
                        && cloudEvt.targets().get(0).storageKey().equals("telegram-clone/user/uuid_clip")
                        && cloudEvt.targets().get(0).resourceType().equals("video")));
    }

    @Test
    @DisplayName("Should keep shared/forwarded video on Cloudinary when another MessageMedia still references it")
    void deleteMessage_withForwardedVideo_shouldNotDeleteCloudAsset() {
        UUID mediaId = UUID.randomUUID();
        Media video = Media.builder()
                .id(mediaId)
                .storageKey("telegram-clone/user/shared_clip")
                .resourceType("video")
                .mimeType("video/mp4")
                .build();
        MessageMedia mm = MessageMedia.builder()
                .id(new MessageMediaId(messageId, mediaId))
                .message(message)
                .media(video)
                .ordinal(0)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageMediaRepository.findByMessageIdInWithMedia(List.of(messageId))).thenReturn(List.of(mm));
        when(messageMediaRepository.existsByMediaId(mediaId)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        messageService.deleteMessage(currentUserId, messageId);

        verify(messageMediaRepository).deleteByMessageId(messageId);
        verify(mediaRepository, never()).saveAll(any());
        verify(mediaRepository, never()).deleteAll(any());
        verify(eventPublisher, never()).publishEvent(any(MediaCloudDeleteEvent.class));
    }
}
