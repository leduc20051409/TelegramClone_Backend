package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.ConversationMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.repository.UnreadCounterRepository;
import com.leanhduc.telegramclone.model.UnreadCounterId;
import com.leanhduc.telegramclone.model.PinnedMessage;
import com.leanhduc.telegramclone.repository.PinnedMessageRepository;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.UpdateConversationRequest;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.repository.MediaRepository;
import java.util.List;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import java.util.UUID;
import com.leanhduc.telegramclone.repository.MessageMediaRepository;
import com.leanhduc.telegramclone.repository.MessagePostViewRepository;
import com.leanhduc.telegramclone.model.MessageMedia;
import com.leanhduc.telegramclone.model.MessagePostView;
import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
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

    @Override
    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Optional<Conversation> existingConversation = conversationRepository
                .findPrivateConversationByUsers(currentUserId, targetUserId);

        if (existingConversation.isPresent()) {
            Conversation conv = existingConversation.get();
            List<Message> latestMessages = messageRepository.findByConversationIdAndDeletedFalseOrderByIdDesc(
                    conv.getId(), PageRequest.of(0, 1)
            );
            Message lastMsg = latestMessages.isEmpty() ? null : latestMessages.get(0);
            List<UserDto> participants = memberRepository.findByConversationIdAndLeftAtIsNull(conv.getId()).stream()
                    .map(member -> {
                        User u = member.getUser();
                        return new UserDto(
                                u.getId(),
                                u.getUsername(),
                                u.getDisplayName(),
                                u.getEmail(),
                                u.getBio(),
                                u.getAvatarMediaId(),
                                u.getRole(),
                                member.getRole() != null ? member.getRole().name() : null
                        );
                    })
                    .toList();
            Long lastReadMsgId = unreadCounterRepository.findById(new UnreadCounterId(conv.getId(), currentUserId))
                    .map(com.leanhduc.telegramclone.model.UnreadCounter::getLastReadMessageId)
                    .orElse(0L);
            int unreadCount = (int) messageRepository.countUnreadMessages(conv.getId(), currentUserId, lastReadMsgId);
            return new ConversationResponse(
                    conv.getId(),
                    conv.getType(),
                    conv.getTitle(),
                    conv.getCreatedAt(),
                    lastMsg != null ? lastMsg.getBody() : null,
                    lastMsg != null ? lastMsg.getCreatedAt() : null,
                    targetUserId,
                    null,
                    null,
                    null,
                    participants,
                    lastMsg != null && lastMsg.getSender() != null ? lastMsg.getSender().getId() : null,
                    unreadCount,
                    getPinnedMessagesForConversation(conv.getId())
            );
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

        List<UserDto> participants = List.of(
                new UserDto(currentUser.getId(), currentUser.getUsername(), currentUser.getDisplayName(), currentUser.getEmail(), currentUser.getBio(), currentUser.getAvatarMediaId(), currentUser.getRole()),
                new UserDto(targetUser.getId(), targetUser.getUsername(), targetUser.getDisplayName(), targetUser.getEmail(), targetUser.getBio(), targetUser.getAvatarMediaId(), targetUser.getRole())
        );

        return new ConversationResponse(
                newConversation.getId(),
                newConversation.getType(),
                newConversation.getTitle(),
                newConversation.getCreatedAt(),
                null,
                null,
                targetUserId,
                null,
                null,
                null,
                participants,
                null,
                0,
                List.of()
        );
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
                .map(conv -> {
                    UUID partnerId = null;
                    if (conv.getType() == ConversationType.PRIVATE) {
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

                    String avatarUrl = null;
                    if (conv.getAvatarMediaId() != null) {
                        avatarUrl = mediaRepository.findById(conv.getAvatarMediaId())
                                .map(com.leanhduc.telegramclone.model.Media::getUrl)
                                .orElse(null);
                    }

                    List<UserDto> participants = memberRepository.findByConversationIdAndLeftAtIsNull(conv.getId()).stream()
                            .map(member -> {
                                User u = member.getUser();
                                return new UserDto(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getDisplayName(),
                                        u.getEmail(),
                                        u.getBio(),
                                        u.getAvatarMediaId(),
                                        u.getRole(),
                                        member.getRole() != null ? member.getRole().name() : null
                                );
                            })
                            .toList();

                    Long lastReadMsgId = unreadCounterRepository.findById(new UnreadCounterId(conv.getId(), userId))
                            .map(com.leanhduc.telegramclone.model.UnreadCounter::getLastReadMessageId)
                            .orElse(0L);
                    int unreadCount = (int) messageRepository.countUnreadMessages(conv.getId(), userId, lastReadMsgId);

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
                            getPinnedMessagesForConversation(conv.getId())
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public ConversationResponse createGroupConversation(UUID creatorUserId, CreateGroupRequest request) {
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
                User memberUser = userRepository.findById(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                ConversationMember member = ConversationMember.builder()
                        .id(new ConversationMemberId(conversation.getId(), memberId))
                        .conversation(conversation)
                        .user(memberUser)
                        .role(ConversationRole.MEMBER)
                        .build();
                memberRepository.save(member);
            }
        }

        String avatarUrl = null;
        if (conversation.getAvatarMediaId() != null) {
            avatarUrl = mediaRepository.findById(conversation.getAvatarMediaId())
                    .map(com.leanhduc.telegramclone.model.Media::getUrl)
                    .orElse(null);
        }

        List<UserDto> participants = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).stream()
                .map(member -> {
                    User u = member.getUser();
                    return new UserDto(
                            u.getId(),
                            u.getUsername(),
                            u.getDisplayName(),
                            u.getEmail(),
                            u.getBio(),
                            u.getAvatarMediaId(),
                            u.getRole(),
                            member.getRole() != null ? member.getRole().name() : null
                    );
                })
                .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                null,
                null,
                null,
                avatarUrl,
                conversation.getAvatarMediaId(),
                conversation.getDescription(),
                participants,
                null,
                0,
                List.of()
        );
    }

    @Override
    @Transactional
    public void leaveConversation(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));

        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        member.setLeftAt(java.time.Instant.now());
        memberRepository.save(member);

        // If the owner left, transfer ownership to another admin or active member if any exist
        if (member.getRole() == ConversationRole.OWNER) {
            List<ConversationMember> activeMembers = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId);
            if (!activeMembers.isEmpty()) {
                // Find first admin or first member
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
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        // Requester must be active member OR they are joining a public conversation themselves
        boolean isSelfJoin = conversation.isPublic() && requesterId.equals(targetUserId);

        ConversationMember requesterMember = null;
        if (!isSelfJoin) {
            requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
            if (requesterMember.getLeftAt() != null) {
                throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
            }
        }

        // If it is a CHANNEL, requester must be OWNER or ADMIN (unless it is a public self-join)
        if (conversation.getType() == ConversationType.CHANNEL && !isSelfJoin &&
                requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if target user is already an active member
        Optional<ConversationMember> targetMemberOpt = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId));
        if (targetMemberOpt.isPresent()) {
            ConversationMember targetMember = targetMemberOpt.get();
            if (targetMember.getLeftAt() == null) {
                throw new BusinessException(ErrorCode.CONTACT_ALREADY_EXISTS);
            }
            // User had left, let's reactivate them
            targetMember.setLeftAt(null);
            targetMember.setJoinedAt(java.time.Instant.now());
            targetMember.setRole(ConversationRole.MEMBER);
            memberRepository.save(targetMember);
        } else {
            // New member
            ConversationMember newMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, targetUserId))
                    .conversation(conversation)
                    .user(targetUser)
                    .role(ConversationRole.MEMBER)
                    .joinedAt(java.time.Instant.now())
                    .build();
            memberRepository.save(newMember);
        }

        String avatarUrl = null;
        if (conversation.getAvatarMediaId() != null) {
            avatarUrl = mediaRepository.findById(conversation.getAvatarMediaId())
                    .map(com.leanhduc.telegramclone.model.Media::getUrl)
                    .orElse(null);
        }

        List<UserDto> participants = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).stream()
                .map(m -> {
                    User u = m.getUser();
                    return new UserDto(
                            u.getId(),
                            u.getUsername(),
                            u.getDisplayName(),
                            u.getEmail(),
                            u.getBio(),
                            u.getAvatarMediaId(),
                            u.getRole(),
                            m.getRole() != null ? m.getRole().name() : null
                    );
                })
                .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                null,
                null,
                null,
                avatarUrl,
                conversation.getAvatarMediaId(),
                conversation.getDescription(),
                participants,
                null,
                0,
                getPinnedMessagesForConversation(conversation.getId())
        );
    }

    @Override
    @Transactional
    public ConversationResponse updateConversation(UUID requesterId, UUID conversationId, UpdateConversationRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        // Requester must be active member
        ConversationMember requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (requesterMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        // Requester must be OWNER or ADMIN to edit details
        if (requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }

        // Validate and trim title
        if (request.getTitle() != null) {
            String trimmedTitle = request.getTitle().trim();
            if (trimmedTitle.isEmpty() || trimmedTitle.length() > 100) {
                throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TITLE);
            }
            conversation.setTitle(trimmedTitle);
        }

        // Validate description
        if (request.getDescription() != null) {
            String trimmedDesc = request.getDescription().trim();
            if (trimmedDesc.length() > 1000) {
                throw new BusinessException(ErrorCode.INVALID_CONVERSATION_DESCRIPTION);
            }
            conversation.setDescription(trimmedDesc);
        }

        // Handle avatar clearing or updating
        if (request.getClearAvatar() != null && request.getClearAvatar()) {
            conversation.setAvatarMediaId(null);
        } else if (request.getAvatarMediaId() != null) {
            conversation.setAvatarMediaId(request.getAvatarMediaId());
        }

        conversation = conversationRepository.save(conversation);

        String avatarUrl = null;
        if (conversation.getAvatarMediaId() != null) {
            avatarUrl = mediaRepository.findById(conversation.getAvatarMediaId())
                    .map(com.leanhduc.telegramclone.model.Media::getUrl)
                    .orElse(null);
        }

        List<UserDto> participants = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).stream()
                .map(m -> {
                    User u = m.getUser();
                    return new UserDto(
                            u.getId(),
                            u.getUsername(),
                            u.getDisplayName(),
                            u.getEmail(),
                            u.getBio(),
                            u.getAvatarMediaId(),
                            u.getRole(),
                            m.getRole() != null ? m.getRole().name() : null
                    );
                })
                .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                null,
                null,
                null,
                avatarUrl,
                conversation.getAvatarMediaId(),
                conversation.getDescription(),
                participants,
                null,
                0,
                getPinnedMessagesForConversation(conversation.getId())
        );
    }

    @Override
    @Transactional
    public void removeMember(UUID requesterId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_ADD_SELF);
        }

        // Requester must be active member
        ConversationMember requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (requesterMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        // Requester must be OWNER or ADMIN to remove members
        if (requesterMember.getRole() != ConversationRole.OWNER &&
                requesterMember.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Target cannot be OWNER
        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        // ADMIN cannot remove another ADMIN (only OWNER can)
        if (requesterMember.getRole() == ConversationRole.ADMIN && targetMember.getRole() == ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        targetMember.setLeftAt(java.time.Instant.now());
        memberRepository.save(targetMember);
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID requesterId, UUID conversationId, UUID targetUserId, ConversationRole role) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        // Requester must be active OWNER
        ConversationMember requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (requesterMember.getLeftAt() != null || requesterMember.getRole() != ConversationRole.OWNER) {
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
        ConversationMember requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (requesterMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        requesterMember.setMuted(isMuted);
        memberRepository.save(requesterMember);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationType getConversationType(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .map(Conversation::getType)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteConversation(UUID requesterId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        ConversationMember requesterMember = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (requesterMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        if (requesterMember.getRole() != ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        // 1. Delete dependent records
        conversationRepository.deletePinnedMessagesByConversationId(conversationId);
        conversationRepository.deleteUnreadCountersByConversationId(conversationId);
        conversationRepository.deleteMessageMediaByConversationId(conversationId);
        conversationRepository.deleteMessageReactionsByConversationId(conversationId);
        conversationRepository.deleteMessagePostViewsByConversationId(conversationId);
        conversationRepository.deleteConversationMembersByConversationId(conversationId);
        conversationRepository.deleteMessagesByConversationId(conversationId);

        // 2. Delete the conversation itself
        conversationRepository.deleteById(conversationId);
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