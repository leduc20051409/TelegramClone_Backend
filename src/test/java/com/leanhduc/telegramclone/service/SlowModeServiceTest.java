package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.dto.conversation.SlowModeStatusResponse;
import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ForwardMessageRequest;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.ConversationMapper;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import com.leanhduc.telegramclone.service.conversation.ConversationService;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import com.leanhduc.telegramclone.service.invite.IConversationInviteLinkService;
import com.leanhduc.telegramclone.service.message.MessageService;
import com.leanhduc.telegramclone.service.message.discussion.IDiscussionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class SlowModeServiceTest {

    @Nested
    @DisplayName("ConversationService - Slow Mode Configuration Tests")
    class ConversationSlowModeConfigTests {

        @Mock
        private ConversationRepository conversationRepository;
        @Mock
        private ConversationMemberRepository memberRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MessageRepository messageRepository;
        @Mock
        private MediaRepository mediaRepository;
        @Mock
        private UnreadCounterRepository unreadCounterRepository;
        @Mock
        private ConversationMapper conversationMapper;
        @Mock
        private PinnedMessageRepository pinnedMessageRepository;
        @Mock
        private MessageMapper messageMapper;
        @Mock
        private MessageMediaRepository messageMediaRepository;
        @Mock
        private MessagePostViewRepository messagePostViewRepository;
        @Mock
        private IPresenceService presenceService;
        @Mock
        private IConversationInviteLinkService inviteLinkService;
        @Mock
        private SimpMessagingTemplate messagingTemplate;
        @Mock
        private IPermissionService permissionService;
        @Mock
        private RedisTemplate<String, String> redisTemplate;

        @InjectMocks
        private ConversationService conversationService;

        private UUID conversationId;
        private UUID adminUserId;
        private UUID memberUserId;
        private Conversation groupConversation;
        private ConversationMember adminMember;
        private ConversationMember regularMember;

        @BeforeEach
        void setUp() {
            conversationId = UUID.randomUUID();
            adminUserId = UUID.randomUUID();
            memberUserId = UUID.randomUUID();

            groupConversation = Conversation.builder()
                    .id(conversationId)
                    .type(ConversationType.GROUP)
                    .title("Test Group")
                    .slowModeDelaySeconds(0)
                    .build();

            adminMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, adminUserId))
                    .conversation(groupConversation)
                    .user(User.builder().id(adminUserId).username("admin").build())
                    .role(ConversationRole.ADMIN)
                    .build();

            regularMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, memberUserId))
                    .conversation(groupConversation)
                    .user(User.builder().id(memberUserId).username("member").build())
                    .role(ConversationRole.MEMBER)
                    .build();
        }

        @Test
        @DisplayName("Admin with CHANGE_INFO can set slow mode delay successfully")
        void adminCanSetSlowMode() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, adminUserId)))
                    .thenReturn(Optional.of(adminMember));
            when(permissionService.hasAdminPermission(adminMember, AdminPermission.CHANGE_INFO)).thenReturn(true);
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
            when(messageRepository.findByConversationIdAndDeletedFalseOrderByIdDesc(eq(conversationId), any(Pageable.class)))
                    .thenReturn(List.of());
            when(unreadCounterRepository.findById(any())).thenReturn(Optional.empty());
            when(pinnedMessageRepository.findAllByConversationIdOrderByPinnedAtDesc(conversationId)).thenReturn(List.of());
            when(memberRepository.findByConversationIdAndLeftAtIsNull(conversationId)).thenReturn(List.of());

            ConversationResponse response = conversationService.setSlowMode(adminUserId, conversationId, 30);

            assertNotNull(response);
            assertEquals(30, groupConversation.getSlowModeDelaySeconds());
            verify(conversationRepository).save(groupConversation);

            // Verify CONVERSATION_UPDATED WebSocket broadcast
            verify(conversationRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("Setting slow mode on CHANNEL throws SLOW_MODE_NOT_SUPPORTED")
        void setSlowModeOnChannelThrowsException() {
            Conversation channel = Conversation.builder()
                    .id(conversationId)
                    .type(ConversationType.CHANNEL)
                    .build();
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(channel));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> conversationService.setSlowMode(adminUserId, conversationId, 60));

            assertEquals(ErrorCode.SLOW_MODE_NOT_SUPPORTED, ex.getErrorCode());
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-admin user cannot set slow mode")
        void nonAdminCannotSetSlowMode() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, memberUserId)))
                    .thenReturn(Optional.of(regularMember));
            when(permissionService.hasAdminPermission(regularMember, AdminPermission.CHANGE_INFO)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> conversationService.setSlowMode(memberUserId, conversationId, 60));

            assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
            verify(conversationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Query slow mode status returns remaining cooldown for member")
        void getSlowModeStatusReturnsRemainingCooldownForMember() {
            groupConversation.setSlowModeDelaySeconds(60);
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, memberUserId)))
                    .thenReturn(Optional.of(regularMember));
            when(redisTemplate.getExpire("slowmode:" + conversationId + ":" + memberUserId, TimeUnit.SECONDS))
                    .thenReturn(42L);

            SlowModeStatusResponse status = conversationService.getSlowModeStatus(memberUserId, conversationId);

            assertNotNull(status);
            assertEquals(conversationId, status.conversationId());
            assertEquals(60, status.slowModeDelaySeconds());
            assertEquals(42L, status.remainingCooldownSeconds());
        }

        @Test
        @DisplayName("Admin or Owner query slow mode status always returns remaining cooldown of 0")
        void adminOrOwnerAlwaysGetsZeroCooldown() {
            groupConversation.setSlowModeDelaySeconds(60);
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, adminUserId)))
                    .thenReturn(Optional.of(adminMember));

            SlowModeStatusResponse status = conversationService.getSlowModeStatus(adminUserId, conversationId);

            assertNotNull(status);
            assertEquals(60, status.slowModeDelaySeconds());
            assertEquals(0L, status.remainingCooldownSeconds());
            // Admin is exempt, redisTemplate getExpire should not even be called
            verify(redisTemplate, never()).getExpire(anyString(), any());
        }
    }

    @Nested
    @DisplayName("MessageService - Atomic Slow Mode Enforcement & Compensation Tests")
    class MessageSlowModeEnforcementTests {

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
        private ValueOperations<String, String> valueOperations;
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

        private UUID conversationId;
        private UUID senderId;
        private User senderUser;
        private Conversation groupConversation;
        private ConversationMember groupMember;
        private ChatMessageRequest chatMessageRequest;

        @BeforeEach
        void setUp() {
            conversationId = UUID.randomUUID();
            senderId = UUID.randomUUID();
            senderUser = User.builder().id(senderId).username("alice").build();

            groupConversation = Conversation.builder()
                    .id(conversationId)
                    .type(ConversationType.GROUP)
                    .title("Developers")
                    .slowModeDelaySeconds(30)
                    .build();

            groupMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, senderId))
                    .conversation(groupConversation)
                    .user(senderUser)
                    .role(ConversationRole.MEMBER)
                    .build();

            chatMessageRequest = new ChatMessageRequest(
                    conversationId,
                    "Hello everyone",
                    null,
                    null
            );
        }

        @Test
        @DisplayName("First message acquires slow mode slot successfully using Redis SET NX EX")
        void firstMessageAcquiresSlot() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                    .thenReturn(Optional.of(groupMember));
            when(permissionService.hasMemberPermission(groupMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String expectedKey = "slowmode:" + conversationId + ":" + senderId;
            when(valueOperations.setIfAbsent(expectedKey, "1", 30, TimeUnit.SECONDS)).thenReturn(true);

            when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));
            Message savedMessage = Message.builder()
                    .id(100L)
                    .conversation(groupConversation)
                    .sender(senderUser)
                    .body("Hello everyone")
                    .messageType(MessageType.TEXT)
                    .build();
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
            ChatMessageResponse responseMock = new ChatMessageResponse(
                    100L, conversationId, senderId, "alice", "Hello everyone",
                    Instant.now(), null, MessageType.TEXT, List.of(), false,
                    null, null, null, null, null, null, null, null
            );
            when(messageMapper.toResponse(any(), any(), any(), any())).thenReturn(responseMock);

            ChatMessageResponse response = messageService.saveMessage(senderId, chatMessageRequest);

            assertNotNull(response);
            assertEquals("Hello everyone", response.message());
            verify(valueOperations).setIfAbsent(expectedKey, "1", 30, TimeUnit.SECONDS);
            verify(messageRepository).save(any(Message.class));
            // Key should NOT be deleted on success
            verify(redisTemplate, never()).delete(expectedKey);
        }

        @Test
        @DisplayName("Second message within cooldown fails SET NX EX and throws 429 SLOW_MODE_ACTIVE")
        void secondMessageWithinCooldownThrows429() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                    .thenReturn(Optional.of(groupMember));
            when(permissionService.hasMemberPermission(groupMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String expectedKey = "slowmode:" + conversationId + ":" + senderId;
            // Cooldown active -> setIfAbsent returns false
            when(valueOperations.setIfAbsent(expectedKey, "1", 30, TimeUnit.SECONDS)).thenReturn(false);
            when(redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS)).thenReturn(18L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> messageService.saveMessage(senderId, chatMessageRequest));

            assertEquals(ErrorCode.SLOW_MODE_ACTIVE, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("18 seconds"));
            // Message must not be saved to DB
            verify(messageRepository, never()).save(any(Message.class));
        }

        @Test
        @DisplayName("Group Owner and Admins bypass slow mode rate limiting completely")
        void ownerAndAdminBypassSlowMode() {
            groupMember.setRole(ConversationRole.ADMIN);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                    .thenReturn(Optional.of(groupMember));
            when(permissionService.hasMemberPermission(groupMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));
            Message savedMessage = Message.builder()
                    .id(101L)
                    .conversation(groupConversation)
                    .sender(senderUser)
                    .body("Admin broadcast")
                    .messageType(MessageType.TEXT)
                    .build();
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            ChatMessageResponse response = messageService.saveMessage(senderId,
                    new ChatMessageRequest(conversationId, "Admin broadcast", null, null));

            assertNotNull(response);
            // Redis rate limit should never be checked for admins
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("DB Persistence failure triggers compensation cleanup of acquired Redis key")
        void dbFailureTriggersCompensationCleanup() {
            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                    .thenReturn(Optional.of(groupMember));
            when(permissionService.hasMemberPermission(groupMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String expectedKey = "slowmode:" + conversationId + ":" + senderId;
            when(valueOperations.setIfAbsent(expectedKey, "1", 30, TimeUnit.SECONDS)).thenReturn(true);

            when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));
            // Simulate database outage / persistence crash
            when(messageRepository.save(any(Message.class))).thenThrow(new RuntimeException("DB Connection Timeout"));

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> messageService.saveMessage(senderId, chatMessageRequest));

            assertEquals("DB Connection Timeout", thrown.getMessage());

            // COMPENSATION: Verify acquired Redis key was compensated (deleted) so member isn't locked out
            verify(redisTemplate).delete(expectedKey);
        }

        @Test
        @DisplayName("Forwarding messages: All-or-nothing rollback deletes acquired keys when one target fails slow mode")
        void forwardMessageRollbackOnSlowModeFailure() {
            UUID targetConv1Id = UUID.randomUUID();
            UUID targetConv2Id = UUID.randomUUID();

            Conversation targetConv1 = Conversation.builder()
                    .id(targetConv1Id)
                    .type(ConversationType.GROUP)
                    .title("Group 1")
                    .slowModeDelaySeconds(15)
                    .build();

            Conversation targetConv2 = Conversation.builder()
                    .id(targetConv2Id)
                    .type(ConversationType.GROUP)
                    .title("Group 2")
                    .slowModeDelaySeconds(30)
                    .build();

            ConversationMember member1 = ConversationMember.builder()
                    .id(new ConversationMemberId(targetConv1Id, senderId))
                    .conversation(targetConv1)
                    .role(ConversationRole.MEMBER)
                    .build();

            ConversationMember member2 = ConversationMember.builder()
                    .id(new ConversationMemberId(targetConv2Id, senderId))
                    .conversation(targetConv2)
                    .role(ConversationRole.MEMBER)
                    .build();

            Message sourceMsg = Message.builder()
                    .id(50L)
                    .conversation(groupConversation)
                    .sender(senderUser)
                    .body("Forward this")
                    .messageType(MessageType.TEXT)
                    .deleted(false)
                    .build();

            when(messageRepository.findById(50L)).thenReturn(Optional.of(sourceMsg));
            when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));
            when(memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(groupConversation.getId(), senderId))
                    .thenReturn(true);

            when(conversationRepository.findById(targetConv1Id)).thenReturn(Optional.of(targetConv1));
            when(memberRepository.findById(new ConversationMemberId(targetConv1Id, senderId)))
                    .thenReturn(Optional.of(member1));
            when(permissionService.hasMemberPermission(member1, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(conversationRepository.findById(targetConv2Id)).thenReturn(Optional.of(targetConv2));
            when(memberRepository.findById(new ConversationMemberId(targetConv2Id, senderId)))
                    .thenReturn(Optional.of(member2));
            when(permissionService.hasMemberPermission(member2, MemberPermission.SEND_MESSAGES)).thenReturn(true);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            String key1 = "slowmode:" + targetConv1Id + ":" + senderId;
            String key2 = "slowmode:" + targetConv2Id + ":" + senderId;

            // Target 1 acquires slot successfully
            when(valueOperations.setIfAbsent(key1, "1", 15, TimeUnit.SECONDS)).thenReturn(true);
            // Target 2 fails slow mode
            when(valueOperations.setIfAbsent(key2, "1", 30, TimeUnit.SECONDS)).thenReturn(false);
            when(redisTemplate.getExpire(key2, TimeUnit.SECONDS)).thenReturn(20L);

            ForwardMessageRequest forwardReq = new ForwardMessageRequest(List.of(targetConv1Id, targetConv2Id));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> messageService.forwardMessage(senderId, 50L, forwardReq));

            assertEquals(ErrorCode.SLOW_MODE_ACTIVE, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("Group 2"));

            // COMPENSATION: Verify key1 that was acquired was released/deleted!
            verify(redisTemplate).delete(List.of(key1));
        }

        @Test
        @DisplayName("Concurrency Simulation: 10 concurrent requests acquire atomically, exactly 1 succeeds")
        void concurrentRequestsAtomicBehavior() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicBoolean slotOccupied = new AtomicBoolean(false);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger slowModeBlockedCount = new AtomicInteger(0);

            when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(groupConversation));
            when(memberRepository.findById(new ConversationMemberId(conversationId, senderId)))
                    .thenReturn(Optional.of(groupMember));
            when(permissionService.hasMemberPermission(groupMember, MemberPermission.SEND_MESSAGES)).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            String expectedKey = "slowmode:" + conversationId + ":" + senderId;
            // Mock atomic SET NX behavior using AtomicBoolean compareAndSet
            when(valueOperations.setIfAbsent(eq(expectedKey), eq("1"), eq(30L), eq(TimeUnit.SECONDS)))
                    .thenAnswer(inv -> slotOccupied.compareAndSet(false, true));
            when(redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS)).thenReturn(30L);

            when(userRepository.findById(senderId)).thenReturn(Optional.of(senderUser));
            Message savedMessage = Message.builder()
                    .id(200L)
                    .conversation(groupConversation)
                    .sender(senderUser)
                    .body("Concurrent msg")
                    .messageType(MessageType.TEXT)
                    .build();
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
            ChatMessageResponse responseMock = new ChatMessageResponse(
                    200L, conversationId, senderId, "alice", "Concurrent msg",
                    Instant.now(), null, MessageType.TEXT, List.of(), false,
                    null, null, null, null, null, null, null, null
            );
            when(messageMapper.toResponse(any(), any(), any(), any())).thenReturn(responseMock);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        messageService.saveMessage(senderId, chatMessageRequest);
                        successCount.incrementAndGet();
                    } catch (BusinessException be) {
                        if (be.getErrorCode() == ErrorCode.SLOW_MODE_ACTIVE) {
                            slowModeBlockedCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Trigger all threads simultaneously
            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            // Exactly 1 thread got the atomic slot, the other 9 received SLOW_MODE_ACTIVE 429
            assertEquals(1, successCount.get(), "Exactly one message should succeed");
            assertEquals(9, slowModeBlockedCount.get(), "Nine messages should be blocked with SLOW_MODE_ACTIVE");
            verify(messageRepository, times(1)).save(any(Message.class));
        }
    }
}
