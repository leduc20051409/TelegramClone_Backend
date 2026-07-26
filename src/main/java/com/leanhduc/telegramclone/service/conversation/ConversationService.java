package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.UpdateConversationRequest;
import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.ConversationMapper;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService implements IConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MediaRepository mediaRepository;
    private final UnreadCounterRepository unreadCounterRepository;
    private final ConversationMapper conversationMapper;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final MessageMapper messageMapper;
    private final MessageMediaRepository messageMediaRepository;
    private final MessagePostViewRepository messagePostViewRepository;
    private final IPresenceService presenceService;

    @Override
    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        User targetUser = getUserOrThrow(targetUserId);
        User currentUser = getUserOrThrow(currentUserId);

        Optional<Conversation> existingConversation = conversationRepository
                .findPrivateConversationByUsers(currentUserId, targetUserId);

        if (existingConversation.isPresent()) {
            return mapToConversationResponse(existingConversation.get(), currentUserId);
        }

        Conversation newConversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .build();
        newConversation = conversationRepository.save(newConversation);

        ConversationMember member1 = ConversationMember.builder()
                .id(new ConversationMemberId(newConversation.getId(), currentUser.getId()))
                .conversation(newConversation)
                .user(currentUser)
                .role(ConversationRole.MEMBER)
                .build();
        ConversationMember member2 = ConversationMember.builder()
                .id(new ConversationMemberId(newConversation.getId(), targetUser.getId()))
                .conversation(newConversation)
                .user(targetUser)
                .role(ConversationRole.MEMBER)
                .build();
        memberRepository.saveAll(List.of(member1, member2));

        return mapToConversationResponse(newConversation, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getConversationMemberIds(UUID conversationId) {
        return memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(member -> member.getUser().getId())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getAllConversationsByUser(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllByMember(userId);
        return conversations.stream()
                .map(conv -> mapToConversationResponse(conv, userId))
                .toList();
    }

    @Override
    @Transactional
    public ConversationResponse createGroupConversation(UUID creatorUserId, CreateGroupRequest request) {
        User creator = getUserOrThrow(creatorUserId);

        ConversationType type = request.getType() != null ? request.getType() : ConversationType.GROUP;

        Conversation conversation = Conversation.builder()
                .type(type)
                .title(request.getTitle())
                .description(request.getDescription())
                .avatarMediaId(request.getAvatarMediaId())
                .createdBy(creatorUserId)
                .build();

        conversation = conversationRepository.save(conversation);

        // Add creator as OWNER
        ConversationMember creatorMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversation.getId(), creatorUserId))
                .conversation(conversation)
                .user(creator)
                .role(ConversationRole.OWNER)
                .build();
        memberRepository.save(creatorMember);

        // Add other members as MEMBER
        if (request.getMemberIds() != null) {
            for (UUID memberId : request.getMemberIds()) {
                if (memberId.equals(creatorUserId)) continue;
                User memberUser = getUserOrThrow(memberId);

                ConversationMember member = ConversationMember.builder()
                        .id(new ConversationMemberId(conversation.getId(), memberId))
                        .conversation(conversation)
                        .user(memberUser)
                        .role(ConversationRole.MEMBER)
                        .build();
                memberRepository.save(member);
            }
        }

        return mapToConversationResponse(conversation, creatorUserId);
    }

    @Override
    @Transactional
    public void leaveConversation(UUID userId, UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.NOT_IN_CONVERSATION);

        ConversationMember member = getActiveMemberOrThrow(conversationId, userId);

        member.setLeftAt(java.time.Instant.now());
        memberRepository.save(member);

        // If the owner left, transfer ownership to another admin or active member if any exist
        if (member.getRole() == ConversationRole.OWNER) {
            List<ConversationMember> activeMembers = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId);
            if (!activeMembers.isEmpty()) {
                ConversationMember newOwner = activeMembers.stream()
                        .filter(m -> m.getRole() == ConversationRole.ADMIN)
                        .findFirst()
                        .orElse(activeMembers.get(0));

                newOwner.setRole(ConversationRole.OWNER);
                memberRepository.save(newOwner);
            }
        }
    }

    @Override
    @Transactional
    public ConversationResponse addMember(UUID requesterId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        boolean isSelfJoin = conversation.isPublic() && requesterId.equals(targetUserId);

        ConversationMember requesterMember = null;
        if (!isSelfJoin) {
            requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        }

        if (conversation.getType() == ConversationType.CHANNEL && !isSelfJoin &&
                requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        User targetUser = getUserOrThrow(targetUserId);

        Optional<ConversationMember> targetMemberOpt = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId));
        if (targetMemberOpt.isPresent()) {
            ConversationMember targetMember = targetMemberOpt.get();
            if (targetMember.getLeftAt() == null) {
                throw new BusinessException(ErrorCode.CONTACT_ALREADY_EXISTS);
            }
            targetMember.setLeftAt(null);
            targetMember.setJoinedAt(java.time.Instant.now());
            targetMember.setRole(ConversationRole.MEMBER);
            memberRepository.save(targetMember);
        } else {
            ConversationMember newMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, targetUserId))
                    .conversation(conversation)
                    .user(targetUser)
                    .role(ConversationRole.MEMBER)
                    .joinedAt(java.time.Instant.now())
                    .build();
            memberRepository.save(newMember);
        }

        return mapToConversationResponse(conversation, requesterId);
    }

    @Override
    @Transactional
    public ConversationResponse updateConversation(UUID requesterId, UUID conversationId, UpdateConversationRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

        if (requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }

        if (request.getTitle() != null) {
            String trimmedTitle = request.getTitle().trim();
            if (trimmedTitle.isEmpty() || trimmedTitle.length() > 100) {
                throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TITLE);
            }
            conversation.setTitle(trimmedTitle);
        }

        if (request.getDescription() != null) {
            String trimmedDesc = request.getDescription().trim();
            if (trimmedDesc.length() > 1000) {
                throw new BusinessException(ErrorCode.INVALID_CONVERSATION_DESCRIPTION);
            }
            conversation.setDescription(trimmedDesc);
        }

        if (request.getClearAvatar() != null && request.getClearAvatar()) {
            conversation.setAvatarMediaId(null);
        } else if (request.getAvatarMediaId() != null) {
            conversation.setAvatarMediaId(request.getAvatarMediaId());
        }

        if (request.getIsPublic() != null) {
            boolean newIsPublic = request.getIsPublic();
            if (newIsPublic) {
                String targetUsername = request.getUsername() != null ? request.getUsername().trim() : conversation.getUsername();
                if (targetUsername == null || targetUsername.isEmpty()) {
                    throw new BusinessException(ErrorCode.USERNAME_REQUIRED_FOR_PUBLIC);
                }
                conversation.setPublic(true);
            } else {
                conversation.setPublic(false);
                conversation.setUsername(null);
            }
        }

        if (request.getUsername() != null) {
            String trimmedUsername = request.getUsername().trim();
            if (trimmedUsername.isEmpty()) {
                if (conversation.isPublic()) {
                    throw new BusinessException(ErrorCode.USERNAME_REQUIRED_FOR_PUBLIC);
                }
                conversation.setUsername(null);
            } else {
                if (trimmedUsername.length() < 3 || trimmedUsername.length() > 32 || !trimmedUsername.matches("^[a-z0-9_]+$")) {
                    throw new BusinessException(ErrorCode.INVALID_USERNAME_FORMAT);
                }
                Optional<Conversation> existing = conversationRepository.findByUsername(trimmedUsername);
                if (existing.isPresent() && !existing.get().getId().equals(conversation.getId())) {
                    throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
                }
                conversation.setUsername(trimmedUsername);
            }
        }

        conversation = conversationRepository.save(conversation);

        return mapToConversationResponse(conversation, requesterId);
    }

    @Override
    @Transactional
    public void removeMember(UUID requesterId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

        if (requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        if (requesterMember.getRole() == ConversationRole.ADMIN && targetMember.getRole() == ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        targetMember.setLeftAt(java.time.Instant.now());
        memberRepository.save(targetMember);
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID requesterId, UUID conversationId, UUID targetUserId, ConversationRole role) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        if (requesterMember.getRole() != ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (role == ConversationRole.OWNER) {
            requesterMember.setRole(ConversationRole.ADMIN);
            memberRepository.save(requesterMember);
            targetMember.setRole(ConversationRole.OWNER);
            memberRepository.save(targetMember);
        } else {
            targetMember.setRole(role);
            memberRepository.save(targetMember);
        }
    }

    @Override
    @Transactional
    public void updateMemberMute(UUID requesterId, UUID conversationId, boolean isMuted) {
        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        requesterMember.setMuted(isMuted);
        memberRepository.save(requesterMember);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationType getConversationType(UUID conversationId) {
        return getConversationOrThrow(conversationId).getType();
    }

    @Override
    @Transactional
    public void deleteConversation(UUID requesterId, UUID conversationId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

        if (requesterMember.getRole() != ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        // Delete dependent records
        conversationRepository.deletePinnedMessagesByConversationId(conversationId);
        conversationRepository.deleteUnreadCountersByConversationId(conversationId);
        conversationRepository.deleteMessageMediaByConversationId(conversationId);
        conversationRepository.deleteMessageReactionsByConversationId(conversationId);
        conversationRepository.deleteMessagePostViewsByConversationId(conversationId);
        conversationRepository.deleteConversationMembersByConversationId(conversationId);
        conversationRepository.deleteMessagesByConversationId(conversationId);

        // Delete conversation itself
        conversationRepository.deleteById(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> searchPublicConversations(String query) {
        String trimmedQuery = query != null ? query.trim() : "";
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }
        List<Conversation> conversations = conversationRepository.searchPublicConversations(trimmedQuery, PageRequest.of(0, 15));
        return conversations.stream()
                .map(conv -> mapToConversationResponse(conv, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getPublicConversationByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        Conversation conv = conversationRepository.findByUsername(username.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        return mapToConversationResponse(conv, null);
    }

    // ==========================================
    // HELPER METHODS FOR REFACTORING & DRY CODE
    // ==========================================

    private Conversation getConversationOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private ConversationMember getActiveMemberOrThrow(UUID conversationId, UUID userId) {
        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
        return member;
    }

    private void validateNotPrivate(Conversation conversation, ErrorCode errorCode) {
        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(errorCode);
        }
    }

    private String resolveMediaUrl(UUID mediaId) {
        if (mediaId == null) return null;
        return mediaRepository.findById(mediaId)
                .map(Media::getUrl)
                .orElse(null);
    }

    private UserDto mapToUserDto(ConversationMember member) {
        User u = member.getUser();
        UserDto dto = new UserDto(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getEmail(),
                u.getBio(),
                u.getAvatarMediaId(),
                u.getRole(),
                member.getRole() != null ? member.getRole().name() : null
        );
        dto.setAvatarUrl(resolveMediaUrl(u.getAvatarMediaId()));
        dto.setOnline(presenceService.isUserOnline(u.getId()));
        dto.setLastSeen(u.getLastSeen());
        return dto;
    }

    private List<UserDto> getParticipants(UUID conversationId) {
        return memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(this::mapToUserDto)
                .toList();
    }

    private ConversationResponse mapToConversationResponse(Conversation conv, UUID userId) {
        UUID partnerId = null;
        if (conv.getType() == ConversationType.PRIVATE && userId != null) {
            partnerId = memberRepository.findByConversationIdAndLeftAtIsNull(conv.getId()).stream()
                    .map(member -> member.getUser().getId())
                    .filter(id -> !id.equals(userId))
                    .findFirst()
                    .orElse(null);
        }

        List<Message> latestMessages = messageRepository.findByConversationIdAndDeletedFalseOrderByIdDesc(
                conv.getId(), PageRequest.of(0, 1)
        );
        Message lastMsg = latestMessages.isEmpty() ? null : latestMessages.get(0);
        String avatarUrl = resolveMediaUrl(conv.getAvatarMediaId());
        List<UserDto> participants = getParticipants(conv.getId());

        int unreadCount = 0;
        if (userId != null) {
            Long lastReadMsgId = unreadCounterRepository.findById(new UnreadCounterId(conv.getId(), userId))
                    .map(UnreadCounter::getLastReadMessageId)
                    .orElse(0L);
            unreadCount = (int) messageRepository.countUnreadMessages(conv.getId(), userId, lastReadMsgId);
        }

        return new ConversationResponse(
                conv.getId(),
                conv.getType(),
                conv.getTitle(),
                conv.getCreatedAt(),
                lastMsg != null ? lastMsg.getBody() : null,
                lastMsg != null ? lastMsg.getCreatedAt() : null,
                partnerId,
                avatarUrl,
                conv.getAvatarMediaId(),
                conv.getDescription(),
                participants,
                lastMsg != null && lastMsg.getSender() != null ? lastMsg.getSender().getId() : null,
                unreadCount,
                getPinnedMessagesForConversation(conv.getId()),
                conv.getUsername(),
                conv.isPublic()
        );
    }

    private List<ChatMessageResponse> getPinnedMessagesForConversation(UUID conversationId) {
        List<PinnedMessage> pinned = pinnedMessageRepository.findAllByConversationIdOrderByPinnedAtDesc(conversationId);
        if (pinned.isEmpty()) {
            return List.of();
        }
        List<Message> messages = pinned.stream()
                .map(PinnedMessage::getMessage)
                .toList();
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

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        boolean isChannel = conversation != null && conversation.getType() == ConversationType.CHANNEL;
        Map<Long, Long> viewCountByMessageId = new HashMap<>();
        if (isChannel) {
            List<MessagePostView> postViews = messagePostViewRepository.findAllById(messageIds);
            viewCountByMessageId = postViews.stream()
                    .collect(Collectors.toMap(MessagePostView::getMessageId, MessagePostView::getViewCount));
        }

        List<ChatMessageResponse> responses = new ArrayList<>(messages.size());
        for (Message message : messages) {
            List<MessageMedia> attachments = mediaByMessageId.getOrDefault(message.getId(), List.of());
            List<MediaAttachmentDto> mediaDtos = attachments.stream()
                    .map(MessageMedia::getMedia)
                    .map(m -> new MediaAttachmentDto(
                            m.getId(),
                            m.getUrl(),
                            m.getMimeType(),
                            m.getFileName(),
                            m.getFileSize() == null ? 0L : m.getFileSize()
                    ))
                    .toList();
            Long viewCount = isChannel ? viewCountByMessageId.getOrDefault(message.getId(), 0L) : null;
            responses.add(messageMapper.toResponse(message, mediaDtos, viewCount));
        }
        return responses;
    }
}