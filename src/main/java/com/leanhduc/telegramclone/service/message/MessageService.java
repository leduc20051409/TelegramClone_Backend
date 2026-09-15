package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.message.DiscussionMediaContext;
import com.leanhduc.telegramclone.dto.message.DiscussionThreadResponse;
import com.leanhduc.telegramclone.dto.message.EditMessageRequest;
import com.leanhduc.telegramclone.dto.message.ForwardMessageRequest;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
import com.leanhduc.telegramclone.dto.message.PinMessageResult;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import com.leanhduc.telegramclone.service.message.discussion.IDiscussionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService implements IMessageService {

    private static final Set<MessageType> FORWARDABLE_MESSAGE_TYPES = Set.of(
            MessageType.TEXT,
            MessageType.IMAGE,
            MessageType.VIDEO,
            MessageType.FILE
    );

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UnreadCounterRepository unreadCounterRepository;
    private final MediaRepository mediaRepository;
    private final MessageMediaRepository messageMediaRepository;
    private final MessageMapper messageMapper;
    private final MessagePostViewRepository messagePostViewRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final DiscussionThreadLinkRepository discussionThreadLinkRepository;
    private final IDiscussionService discussionService;
    private final IPermissionService permissionService;
    private final ContactRepository contactRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ChatMessageResponse saveMessage(UUID senderId, ChatMessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        ConversationMember member = memberRepository.findById(new ConversationMemberId(request.conversationId(), senderId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        if (conversation.getType() == ConversationType.CHANNEL) {
            if (!permissionService.hasAdminPermission(member, AdminPermission.POST_MESSAGES)) {
                throw new BusinessException(ErrorCode.SUBSCRIBERS_CANNOT_POST);
            }
        } else if (conversation.getType() == ConversationType.GROUP) {
            if (!permissionService.hasMemberPermission(member, MemberPermission.SEND_MESSAGES)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        } else if (conversation.getType() == ConversationType.PRIVATE) {
            List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId());
            UUID partnerId = members.stream()
                    .map(m -> m.getUser().getId())
                    .filter(id -> !id.equals(senderId))
                    .findFirst()
                    .orElse(null);

            if (partnerId != null) {
                if (contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(partnerId, senderId)) {
                    throw new BusinessException(ErrorCode.USER_BLOCKED);
                }
                if (contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(senderId, partnerId)) {
                    throw new BusinessException(ErrorCode.CANNOT_MESSAGE_BLOCKED_USER);
                }
            }
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<UUID> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        MessageType messageType = mediaIds.isEmpty() ? MessageType.TEXT : MessageType.FILE;
        Map<UUID, Media> mediaById = Collections.emptyMap();
        if (!mediaIds.isEmpty()) {
            if (!permissionService.hasMemberPermission(member, MemberPermission.SEND_MEDIA)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
            Set<UUID> uniqueMediaIds = mediaIds.stream().collect(Collectors.toSet());
            if (uniqueMediaIds.size() != mediaIds.size()) {
                throw new BusinessException(ErrorCode.DUPLICATE_MEDIA);
            }
            List<Media> mediaList = mediaRepository.findByIdInAndOwnerId(new ArrayList<>(uniqueMediaIds), senderId);
            if (mediaList.size() != uniqueMediaIds.size()) {
                throw new BusinessException(ErrorCode.MEDIA_NOT_ACCESSIBLE);
            }
            mediaById = mediaList.stream().collect(Collectors.toMap(Media::getId, media -> media));
        }

        Message replyTo = null;
        if (request.replyToMessageId() != null) {
            replyTo = messageRepository
                    .findByIdAndConversationId(request.replyToMessageId(), request.conversationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .body(request.message())
                .messageType(messageType)
                .deleted(false)
                .replyTo(replyTo)
                .build();

        message = messageRepository.save(message);

        if (!mediaIds.isEmpty()) {
            List<MessageMedia> messageMediaList = new ArrayList<>();
            for (int i = 0; i < mediaIds.size(); i++) {
                UUID mediaId = mediaIds.get(i);
                Media media = mediaById.get(mediaId);
                if (media.getStatus() != MediaStatus.TEMP) {
                    throw new BusinessException(ErrorCode.MEDIA_NOT_ACCESSIBLE);
                }
                MessageMediaId id = new MessageMediaId(message.getId(), mediaId);
                MessageMedia messageMedia = MessageMedia.builder()
                        .id(id)
                        .message(message)
                        .media(media)
                        .ordinal(i)
                        .build();
                messageMediaList.add(messageMedia);
                media.setStatus(MediaStatus.ACTIVE);
            }
            messageMediaRepository.saveAll(messageMediaList);
            mediaRepository.saveAll(mediaById.values());
        }

        List<MediaAttachmentDto> mediaDtos = buildMediaDtos(mediaIds, mediaById);
        Long viewCount = conversation.getType() == ConversationType.CHANNEL ? 0L : null;
        Integer initialCommentCount = null;

        if (conversation.getType() == ConversationType.CHANNEL
                && conversation.getLinkedDiscussionGroupId() != null) {
            DiscussionMediaContext mediaContext =
                    new DiscussionMediaContext(mediaIds, mediaById, mediaDtos);
            initialCommentCount = discussionService.handleChannelPost(message, mediaContext);
        }

        if (conversation.getType() == ConversationType.GROUP && replyTo != null) {
            discussionService.handleCommentCreated(message);
        }

        return messageMapper.toResponse(message, mediaDtos, viewCount, initialCommentCount);
    }


    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessageHistory(UUID conversationId, UUID currentUserId, Long cursor, int size) {
        boolean isMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, currentUserId);
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
        Pageable pageable = PageRequest.of(0, size);
        List<Message> messages;
        if (cursor == null) {
            messages = messageRepository.findByConversationIdAndDeletedFalseOrderByIdDesc(conversationId, pageable);
        } else {
            messages = messageRepository.findMessagesBeforeId(conversationId, cursor, pageable);
        }
        return toResponsesWithMedia(messages);
    }

    @Override
    public void markMessagesAsRead(UUID currentUserId, ChatReadRequest request) {
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(request.conversationId(), currentUserId)) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
        Conversation conversationRef = conversationRepository.getReferenceById(request.conversationId());
        User userRef = userRepository.getReferenceById(currentUserId);
        UnreadCounterId counterId = new UnreadCounterId(request.conversationId(), currentUserId);
        UnreadCounter counter = unreadCounterRepository.findById(counterId)
                .orElse(UnreadCounter.builder()
                        .id(counterId)
                        .conversation(conversationRef)
                        .user(userRef)
                        .build());
        if (counter.getLastReadMessageId() == null || counter.getLastReadMessageId() < request.lastReadMessageId()) {
            counter.setLastReadMessageId(request.lastReadMessageId());
            unreadCounterRepository.save(counter);
        }
    }

    private List<ChatMessageResponse> toResponsesWithMedia(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = messages.stream()
                .map(Message::getId)
                .toList();
        List<MessageMedia> messageMediaList = messageMediaRepository.findByMessageIdInWithMedia(messageIds);

        Map<Long, List<MessageMedia>> mediaByMessageId = new HashMap<>();
        for (MessageMedia messageMedia : messageMediaList) {
            mediaByMessageId
                    .computeIfAbsent(messageMedia.getMessage().getId(), key -> new ArrayList<>())
                    .add(messageMedia);
        }

        boolean isChannel = !messages.isEmpty() && messages.get(0).getConversation().getType() == ConversationType.CHANNEL;
        Map<Long, Long> viewCountByMessageId = new HashMap<>();
        Map<Long, Integer> commentCountByMessageId = new HashMap<>();
        if (isChannel) {
            List<MessagePostView> postViews = messagePostViewRepository.findAllById(messageIds);
            viewCountByMessageId = postViews.stream()
                    .collect(Collectors.toMap(MessagePostView::getMessageId, MessagePostView::getViewCount));

            List<DiscussionThreadLink> threadLinks = discussionThreadLinkRepository.findByChannelPostMessageIdIn(messageIds);
            for (DiscussionThreadLink link : threadLinks) {
                if (link.getChannelPostMessage() != null) {
                    commentCountByMessageId.put(link.getChannelPostMessage().getId(), link.getCommentCount());
                }
            }
        }

        List<ChatMessageResponse> responses = new ArrayList<>(messages.size());
        for (Message message : messages) {
            List<MessageMedia> attachments = mediaByMessageId.getOrDefault(message.getId(), List.of());
            List<MediaAttachmentDto> mediaDtos = attachments.stream()
                    .map(MessageMedia::getMedia)
                    .map(this::toMediaDto)
                    .toList();
            Long viewCount = isChannel ? viewCountByMessageId.getOrDefault(message.getId(), 0L) : null;
            Integer commentCount = isChannel ? commentCountByMessageId.getOrDefault(message.getId(), 0) : null;
            responses.add(messageMapper.toResponse(message, mediaDtos, viewCount, commentCount));
        }
        return responses;
    }

    private List<MediaAttachmentDto> buildMediaDtos(List<UUID> mediaIds, Map<UUID, Media> mediaById) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        List<MediaAttachmentDto> result = new ArrayList<>(mediaIds.size());
        for (UUID mediaId : mediaIds) {
            Media media = mediaById.get(mediaId);
            if (media != null) {
                result.add(toMediaDto(media));
            }
        }
        return result;
    }

    private MediaAttachmentDto toMediaDto(Media media) {
        return new MediaAttachmentDto(
                media.getId(),
                media.getUrl(),
                media.getMimeType(),
                media.getFileName(),
                media.getFileSize() == null ? 0L : media.getFileSize()
        );
    }

    @Override
    @Transactional
    public ChatMessageResponse editMessage(UUID currentUserId, Long messageId, EditMessageRequest request) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (message.isDeleted()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (!message.getSender().getId().equals(currentUserId)) {
            ConversationMember member = memberRepository.findById(new ConversationMemberId(message.getConversation().getId(), currentUserId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
            if (!permissionService.hasAdminPermission(member, AdminPermission.EDIT_MESSAGES)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
            }
        }

        message.setBody(request.message());
        message.setEdited(true);
        message.setUpdatedAt(java.time.Instant.now());

        List<UUID> mediaIds = request.mediaIds();
        if (mediaIds != null) {
            messageMediaRepository.deleteByMessageId(messageId);

            if (!mediaIds.isEmpty()) {
                Set<UUID> uniqueMediaIds = new HashSet<>(mediaIds);
                if (uniqueMediaIds.size() != mediaIds.size()) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEDIA);
                }
                List<Media> mediaList = mediaRepository.findByIdInAndOwnerId(new ArrayList<>(uniqueMediaIds), currentUserId);
                if (mediaList.size() != uniqueMediaIds.size()) {
                    throw new BusinessException(ErrorCode.MEDIA_NOT_ACCESSIBLE);
                }
                Map<UUID, Media> mediaById = mediaList.stream()
                        .collect(Collectors.toMap(Media::getId, media -> media));

                List<MessageMedia> messageMediaList = new ArrayList<>();
                for (int i = 0; i < mediaIds.size(); i++) {
                    UUID mediaId = mediaIds.get(i);
                    Media media = mediaById.get(mediaId);
                    if (media.getStatus() == MediaStatus.TEMP) {
                        media.setStatus(MediaStatus.ACTIVE);
                    }
                    MessageMediaId mmId = new MessageMediaId(message.getId(), mediaId);
                    MessageMedia messageMedia = MessageMedia.builder()
                            .id(mmId)
                            .message(message)
                            .media(media)
                            .ordinal(i)
                            .build();
                    messageMediaList.add(messageMedia);
                }
                messageMediaRepository.saveAll(messageMediaList);
                mediaRepository.saveAll(mediaList);
            }
        }

        message = messageRepository.save(message);

        List<MessageMedia> messageMediaList = messageMediaRepository.findByMessageIdInWithMedia(List.of(messageId));
        List<MediaAttachmentDto> mediaDtos = messageMediaList.stream()
                .map(MessageMedia::getMedia)
                .map(this::toMediaDto)
                .toList();

        Long viewCount = null;
        if (message.getConversation().getType() == ConversationType.CHANNEL) {
            viewCount = messagePostViewRepository.findById(messageId)
                    .map(MessagePostView::getViewCount)
                    .orElse(0L);
        }

        ChatMessageResponse response = messageMapper.toResponse(message, mediaDtos, viewCount);

        List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(message.getConversation().getId())
                .stream()
                .map(member -> member.getUser().getId())
                .toList();

        eventPublisher.publishEvent(new MessageEditedEvent(
                response,
                message.getConversation().getType(),
                memberIds
        ));

        return response;
    }

    @Override
    @Transactional
    public UUID deleteMessage(UUID currentUserId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (message.isDeleted()) {
            return message.getConversation().getId();
        }

        if (!message.getSender().getId().equals(currentUserId)) {
            ConversationMember member = memberRepository.findById(new ConversationMemberId(message.getConversation().getId(), currentUserId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
            if (!permissionService.hasAdminPermission(member, AdminPermission.DELETE_MESSAGES)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
            }
        }

        message.setDeleted(true);
        messageRepository.save(message);

        UUID conversationId = message.getConversation().getId();
        ConversationType conversationType = message.getConversation().getType();
        List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId)
                .stream()
                .map(member -> member.getUser().getId())
                .toList();

        eventPublisher.publishEvent(new MessageDeletedEvent(
                messageId,
                conversationId,
                conversationType,
                memberIds
        ));

        return conversationId;
    }

    @Override
    @Transactional
    public void incrementViews(UUID userId, UUID conversationId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        boolean isMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        List<Message> messages = messageRepository.findAllById(messageIds);
        for (Message message : messages) {
            if (message.getConversation().getId().equals(conversationId)) {
                String key = "message:views:" + message.getId();
                Boolean alreadyViewed = redisTemplate.opsForSet().isMember(key, userId.toString());
                if (Boolean.FALSE.equals(alreadyViewed)) {
                    redisTemplate.opsForSet().add(key, userId.toString());
                    redisTemplate.expire(key, 30, TimeUnit.DAYS);
                    messagePostViewRepository.incrementViewCount(message.getId());
                }
            }
        }
    }

    @Override
    @Transactional
    public PinMessageResult pinMessage(UUID userId, UUID conversationId, Long messageId) {
        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.GROUP) {
            if (!permissionService.hasMemberPermission(member, MemberPermission.PIN_MESSAGES) &&
                !permissionService.hasAdminPermission(member, AdminPermission.PIN_MESSAGES)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        } else if (conversation.getType() == ConversationType.CHANNEL) {
            if (!permissionService.hasAdminPermission(member, AdminPermission.PIN_MESSAGES)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getConversation().getId().equals(conversationId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        PinnedMessageId pinnedId = new PinnedMessageId(conversationId, messageId);
        PinnedMessage pinnedMessage = PinnedMessage.builder()
                .id(pinnedId)
                .conversation(conversation)
                .message(message)
                .pinnedBy(userId)
                .build();

        pinnedMessageRepository.save(pinnedMessage);

        // 1. Map the pinned message
        ChatMessageResponse pinnedMessageResponse = toResponsesWithMedia(List.of(message)).get(0);

        // 2. Create the SYSTEM notification message
        User pinner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String previewText = "";
        if (message.getBody() != null && !message.getBody().isEmpty()) {
            previewText = message.getBody();
        }

        List<MessageMedia> messageMediaList = messageMediaRepository.findByMessageIdInWithMedia(List.of(messageId));
        if (!messageMediaList.isEmpty()) {
            String prefix = messageMediaList.get(0).getMedia().getMimeType().startsWith("image/") ? "Photo" : "File";
            if (!previewText.isEmpty()) {
                previewText = prefix + ", " + previewText;
            } else {
                previewText = prefix;
            }
        }

        if (previewText.length() > 60) {
            previewText = previewText.substring(0, 57) + "...";
        }

        String displayName = conversation.getType() == ConversationType.CHANNEL ?
                conversation.getTitle() :
                (pinner.getDisplayName() != null ? pinner.getDisplayName() : pinner.getUsername());

        String systemBody = displayName + " pinned \"" + previewText + "\"";

        Message systemMessage = Message.builder()
                .conversation(conversation)
                .sender(pinner)
                .messageType(MessageType.SYSTEM)
                .body(systemBody)
                .build();

        Message savedSystemMsg = messageRepository.save(systemMessage);
        ChatMessageResponse systemMessageResponse = toResponsesWithMedia(List.of(savedSystemMsg)).get(0);

        return new PinMessageResult(pinnedMessageResponse, systemMessageResponse);
    }

    @Override
    @Transactional
    public void unpinMessage(UUID userId, UUID conversationId, Long messageId) {
        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.GROUP) {
            if (!permissionService.hasMemberPermission(member, MemberPermission.PIN_MESSAGES) &&
                !permissionService.hasAdminPermission(member, AdminPermission.PIN_MESSAGES)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        } else if (conversation.getType() == ConversationType.CHANNEL) {
            if (!permissionService.hasAdminPermission(member, AdminPermission.PIN_MESSAGES)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
        }

        PinnedMessageId pinnedId = new PinnedMessageId(conversationId, messageId);
        if (!pinnedMessageRepository.existsById(pinnedId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        pinnedMessageRepository.deleteById(pinnedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(UUID conversationId, UUID currentUserId, String query, String dateStr) {
        boolean isMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, currentUserId);
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        java.time.Instant startDate = null;
        java.time.Instant endDate = null;
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(dateStr);
                startDate = localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                endDate = localDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                // Ignore invalid date format
            }
        }

        String searchPattern = null;
        if (query != null && !query.isBlank()) {
            searchPattern = query.trim();
        }

        if (searchPattern == null && startDate == null) {
            return List.of();
        }

        List<Message> messages;
        if (searchPattern != null && startDate != null) {
            messages = messageRepository.findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseAndCreatedAtBetweenOrderByIdDesc(
                    conversationId,
                    searchPattern,
                    startDate,
                    endDate,
                    PageRequest.of(0, 100)
            );
        } else if (searchPattern != null) {
            messages = messageRepository.findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseOrderByIdDesc(
                    conversationId,
                    searchPattern,
                    PageRequest.of(0, 100)
            );
        } else if (startDate != null) {
            messages = messageRepository.findByConversationIdAndDeletedFalseAndCreatedAtBetweenOrderByIdDesc(
                    conversationId,
                    startDate,
                    endDate,
                    PageRequest.of(0, 100)
            );
        } else {
            messages = List.of();
        }

        return toResponsesWithMedia(messages);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscussionThreadResponse getDiscussionThread(UUID currentUserId, Long channelPostId, Long cursor, int size) {
        Message channelPost = messageRepository.findById(channelPostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        Conversation channel = channelPost.getConversation();
        if (channel.getType() != ConversationType.CHANNEL) {
            throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TYPES);
        }

        if (!channel.isPublic()) {
            ConversationMember member = memberRepository.findById(new ConversationMemberId(channel.getId(), currentUserId))
                    .orElse(null);
            if (member == null || member.getLeftAt() != null) {
                throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
            }
        }

        DiscussionThreadLink threadLink = discussionThreadLinkRepository.findByChannelPostMessageId(channelPostId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISCUSSION_NOT_LINKED));

        Message groupRootMessage = threadLink.getGroupRootMessage();
        Conversation group = groupRootMessage.getConversation();

        int fetchSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(0, fetchSize);

        List<Message> commentMessages;
        if (cursor == null || cursor <= 0) {
            commentMessages = messageRepository.findThreadComments(groupRootMessage.getId(), pageable);
        } else {
            commentMessages = messageRepository.findThreadCommentsAfterId(groupRootMessage.getId(), cursor, pageable);
        }

        List<ChatMessageResponse> commentResponses = toResponsesWithMedia(commentMessages);
        ChatMessageResponse rootMessageResponse = toResponsesWithMedia(List.of(groupRootMessage)).stream().findFirst().orElse(null);

        return new DiscussionThreadResponse(
                channelPostId,
                channel.getId(),
                groupRootMessage.getId(),
                group.getId(),
                threadLink.getCommentCount(),
                rootMessageResponse,
                commentResponses
        );
    }

    @Override
    @Transactional
    public List<ChatMessageResponse> forwardMessage(UUID currentUserId, Long messageId, ForwardMessageRequest request) {
        if (request.targetConversationIds() == null || request.targetConversationIds().isEmpty()) {
            throw new BusinessException(ErrorCode.NO_TARGET_CONVERSATION);
        }

        Set<UUID> uniqueTargetIds = new HashSet<>(request.targetConversationIds());
        if (uniqueTargetIds.size() != request.targetConversationIds().size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_TARGET_CONVERSATION);
        }

        // 1. Validate Source Message
        Message sourceMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (sourceMessage.isDeleted()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        if (!FORWARDABLE_MESSAGE_TYPES.contains(sourceMessage.getMessageType())) {
            throw new BusinessException(ErrorCode.CANNOT_FORWARD_MESSAGE_TYPE);
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        validateSourceAccess(currentUser, sourceMessage.getConversation());

        // 2. Determine Original Attribution
        User originalSender = sourceMessage.getForwardedFromUser() != null
                ? sourceMessage.getForwardedFromUser()
                : sourceMessage.getSender();

        Conversation originalConversation = sourceMessage.getForwardedFromConversation() != null
                ? sourceMessage.getForwardedFromConversation()
                : sourceMessage.getConversation();

        Instant forwardedAt = Instant.now();

        // 3. Pre-validate ALL target conversations (All-or-Nothing policy)
        Map<UUID, TargetValidationContext> targetContexts = new LinkedHashMap<>();

        for (UUID targetConvId : request.targetConversationIds()) {
            Conversation targetConv = conversationRepository.findById(targetConvId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

            ConversationMember member = memberRepository.findById(new ConversationMemberId(targetConvId, currentUserId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));

            if (member.getLeftAt() != null) {
                throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
            }

            if (targetConv.getType() == ConversationType.CHANNEL) {
                if (!permissionService.hasAdminPermission(member, AdminPermission.POST_MESSAGES)) {
                    throw new BusinessException(ErrorCode.SUBSCRIBERS_CANNOT_POST);
                }
            } else if (targetConv.getType() == ConversationType.GROUP) {
                if (!permissionService.hasMemberPermission(member, MemberPermission.SEND_MESSAGES)) {
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED);
                }
            } else if (targetConv.getType() == ConversationType.PRIVATE) {
                List<ConversationMember> convMembers = memberRepository.findByConversationIdAndLeftAtIsNull(targetConvId);
                UUID partnerId = convMembers.stream()
                        .map(m -> m.getUser().getId())
                        .filter(id -> !id.equals(currentUserId))
                        .findFirst()
                        .orElse(null);

                if (partnerId != null) {
                    if (contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(partnerId, currentUserId)) {
                        throw new BusinessException(ErrorCode.USER_BLOCKED);
                    }
                    if (contactRepository.existsByOwnerIdAndContactIdAndIsBlockedTrue(currentUserId, partnerId)) {
                        throw new BusinessException(ErrorCode.CANNOT_MESSAGE_BLOCKED_USER);
                    }
                }
            }

            List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(targetConvId).stream()
                    .map(m -> m.getUser().getId())
                    .toList();

            targetContexts.put(targetConvId, new TargetValidationContext(targetConv, member, memberIds));
        }

        // 4. Retrieve source media attachments if any
        List<MessageMedia> sourceMediaList = messageMediaRepository.findByMessageIdInWithMedia(List.of(messageId));

        // 5. Create new forwarded messages across all targets
        List<ChatMessageResponse> responses = new ArrayList<>();
        List<MessagesForwardedEvent.TargetBroadcastDto> broadcasts = new ArrayList<>();

        for (UUID targetConvId : request.targetConversationIds()) {
            TargetValidationContext context = targetContexts.get(targetConvId);
            Conversation targetConv = context.conversation();

            Message forwardedMessage = Message.builder()
                    .conversation(targetConv)
                    .sender(currentUser)
                    .body(sourceMessage.getBody())
                    .messageType(sourceMessage.getMessageType())
                    .deleted(false)
                    .forwardedFromUser(originalSender)
                    .forwardedFromConversation(originalConversation)
                    .forwardedAt(forwardedAt)
                    .build();

            forwardedMessage = messageRepository.save(forwardedMessage);

            // Associate existing media to the new message
            List<MediaAttachmentDto> mediaDtos = new ArrayList<>();
            if (!sourceMediaList.isEmpty()) {
                List<MessageMedia> newMediaList = new ArrayList<>();
                for (MessageMedia sm : sourceMediaList) {
                    MessageMedia mm = MessageMedia.builder()
                            .id(new MessageMediaId(forwardedMessage.getId(), sm.getMedia().getId()))
                            .message(forwardedMessage)
                            .media(sm.getMedia())
                            .ordinal(sm.getOrdinal())
                            .build();
                    newMediaList.add(mm);
                    mediaDtos.add(toMediaDto(sm.getMedia()));
                }
                messageMediaRepository.saveAll(newMediaList);
            }

            Long viewCount = targetConv.getType() == ConversationType.CHANNEL ? 0L : null;
            ChatMessageResponse response = messageMapper.toResponse(forwardedMessage, mediaDtos, viewCount, null);
            responses.add(response);

            broadcasts.add(new MessagesForwardedEvent.TargetBroadcastDto(
                    targetConvId,
                    targetConv.getType(),
                    context.memberIds(),
                    response
            ));
        }

        // 6. Publish Event for AFTER_COMMIT WebSocket broadcast
        eventPublisher.publishEvent(new MessagesForwardedEvent(broadcasts));

        return responses;
    }

    private void validateSourceAccess(User currentUser, Conversation sourceConv) {
        if (sourceConv.getType() == ConversationType.CHANNEL && sourceConv.isPublic()) {
            return;
        }
        boolean isMember = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(
                sourceConv.getId(), currentUser.getId()
        );
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
    }

    private record TargetValidationContext(
            Conversation conversation,
            ConversationMember member,
            List<UUID> memberIds
    ) {}
}
