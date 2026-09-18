package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.dto.conversation.*;
import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.user.UserDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.ConversationMapper;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.dto.invite.CreateInviteLinkRequest;
import com.leanhduc.telegramclone.service.Presence.IPresenceService;
import com.leanhduc.telegramclone.service.invite.IConversationInviteLinkService;
import com.leanhduc.telegramclone.dto.websocket.MemberEventResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final IConversationInviteLinkService inviteLinkService;
    private final SimpMessagingTemplate messagingTemplate;
    private final IPermissionService permissionService;

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

        Set<MemberPermission> defaultPermissions = new HashSet<>();
        if (type == ConversationType.GROUP) {
            defaultPermissions.add(MemberPermission.SEND_MESSAGES);
            defaultPermissions.add(MemberPermission.SEND_MEDIA);
            defaultPermissions.add(MemberPermission.SEND_POLLS);
            defaultPermissions.add(MemberPermission.EMBED_LINKS);
            defaultPermissions.add(MemberPermission.ADD_MEMBERS);
        }

        Conversation conversation = Conversation.builder()
                .type(type)
                .title(request.getTitle())
                .description(request.getDescription())
                .avatarMediaId(request.getAvatarMediaId())
                .createdBy(creatorUserId)
                .defaultMemberPermissions(defaultPermissions)
                .build();

        conversation = conversationRepository.save(conversation);

        // Add creator as OWNER with full permissions
        ConversationMember creatorMember = ConversationMember.builder()
                .id(new ConversationMemberId(conversation.getId(), creatorUserId))
                .conversation(conversation)
                .user(creator)
                .role(ConversationRole.OWNER)
                .memberPermissions(EnumSet.allOf(MemberPermission.class))
                .adminPermissions(EnumSet.allOf(AdminPermission.class))
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

        // Auto-generate a Primary Private Invite Link for newly created Group or Channel
        CreateInviteLinkRequest defaultInviteLinkReq = new CreateInviteLinkRequest(
                "Primary Link",
                null,
                0,
                true
        );
        inviteLinkService.createInviteLink(creatorUserId, conversation.getId(), defaultInviteLinkReq);

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
                newOwner.setMemberPermissions(EnumSet.allOf(MemberPermission.class));
                newOwner.setAdminPermissions(EnumSet.allOf(AdminPermission.class));
                memberRepository.save(newOwner);
            }
        }

        User user = member.getUser();
        String displayName = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
        broadcastMemberEventAndSystemMessage(conversation, user, "MEMBER_LEFT", displayName + " left the group");
    }

    @Override
    @Transactional
    public ConversationResponse addMember(UUID requesterId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        boolean isSelfJoin = conversation.isPublic() && requesterId.equals(targetUserId);

        if (!isSelfJoin) {
            ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

            if (conversation.getType() == ConversationType.CHANNEL) {
                if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.INVITE_USERS) &&
                    !permissionService.hasAdminPermission(requesterMember, AdminPermission.ADD_ADMINS)) {
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED);
                }
            } else {
                if (!permissionService.hasMemberPermission(requesterMember, MemberPermission.ADD_MEMBERS) &&
                    !permissionService.hasAdminPermission(requesterMember, AdminPermission.INVITE_USERS)) {
                    throw new BusinessException(ErrorCode.PERMISSION_DENIED);
                }
            }
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

        User requesterUser = getUserOrThrow(requesterId);
        String targetName = targetUser.getDisplayName() != null && !targetUser.getDisplayName().isBlank()
                ? targetUser.getDisplayName() : targetUser.getUsername();
        String requesterName = requesterUser.getDisplayName() != null && !requesterUser.getDisplayName().isBlank()
                ? requesterUser.getDisplayName() : requesterUser.getUsername();

        String systemText = isSelfJoin || requesterId.equals(targetUserId)
                ? targetName + " joined the group"
                : requesterName + " added " + targetName + " to the group";

        broadcastMemberEventAndSystemMessage(conversation, targetUser, "MEMBER_JOINED", systemText);

        return mapToConversationResponse(conversation, requesterId);
    }

    @Override
    @Transactional
    public ConversationResponse updateConversation(UUID requesterId, UUID conversationId, UpdateConversationRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

        if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.CHANGE_INFO)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
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

        if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.BAN_USERS)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        if (requesterMember.getRole() == ConversationRole.ADMIN && targetMember.getRole() == ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        targetMember.setLeftAt(java.time.Instant.now());
        memberRepository.save(targetMember);

        User targetUser = targetMember.getUser();
        User requester = getUserOrThrow(requesterId);
        String targetName = targetUser.getDisplayName() != null && !targetUser.getDisplayName().isBlank()
                ? targetUser.getDisplayName() : targetUser.getUsername();
        String requesterName = requester.getDisplayName() != null && !requester.getDisplayName().isBlank()
                ? requester.getDisplayName() : requester.getUsername();

        broadcastMemberEventAndSystemMessage(conversation, targetUser, "MEMBER_LEFT", requesterName + " removed " + targetName + " from the group");
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID requesterId, UUID conversationId, UUID targetUserId, ConversationRole role) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);

        if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.ADD_ADMINS)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        if (role == ConversationRole.OWNER) {
            if (requesterMember.getRole() != ConversationRole.OWNER) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }
            requesterMember.setRole(ConversationRole.ADMIN);
            memberRepository.save(requesterMember);
            targetMember.setRole(ConversationRole.OWNER);
            targetMember.setMemberPermissions(EnumSet.allOf(MemberPermission.class));
            targetMember.setAdminPermissions(EnumSet.allOf(AdminPermission.class));
            memberRepository.save(targetMember);
        } else {
            targetMember.setRole(role);
            if (role == ConversationRole.ADMIN) {
                if (targetMember.getAdminPermissions() == null || targetMember.getAdminPermissions().isEmpty()) {
                    targetMember.setAdminPermissions(new HashSet<>(List.of(
                            AdminPermission.CHANGE_INFO,
                            AdminPermission.DELETE_MESSAGES,
                            AdminPermission.BAN_USERS,
                            AdminPermission.INVITE_USERS,
                            AdminPermission.PIN_MESSAGES
                    )));
                }
            } else {
                targetMember.getAdminPermissions().clear();
            }
            memberRepository.save(targetMember);
        }

        ConversationResponse updatedResponse = mapToConversationResponse(conversation, null);
        WsEnvelope<ConversationResponse> envelope = WsEnvelope.of("CONVERSATION_UPDATED", updatedResponse);
        broadcastEnvelopeToMembers(conversation, envelope, null);
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
    // PERMISSIONS MANAGEMENT IMPLEMENTATION
    // ==========================================

    @Override
    @Transactional
    public void updateDefaultPermissions(UUID requesterId, UUID conversationId, UpdateDefaultPermissionsRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.CHANGE_INFO)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        Set<MemberPermission> newDefaults = request.permissions() != null ? request.permissions() : new HashSet<>();
        conversation.setDefaultMemberPermissions(newDefaults);
        conversationRepository.save(conversation);
    }

    @Override
    @Transactional
    public void updateMemberPermissions(UUID requesterId, UUID conversationId, UUID targetUserId, UpdateMemberPermissionsRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        if (!permissionService.hasAdminPermission(requesterMember, AdminPermission.BAN_USERS)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        Set<MemberPermission> newPermissions = request.permissions() != null ? request.permissions() : new HashSet<>();
        targetMember.setMemberPermissions(newPermissions);
        memberRepository.save(targetMember);
    }

    @Override
    @Transactional
    public void updateAdminPermissions(UUID requesterId, UUID conversationId, UUID targetUserId, UpdateAdminPermissionsRequest request) {
        Conversation conversation = getConversationOrThrow(conversationId);
        validateNotPrivate(conversation, ErrorCode.CANNOT_CHAT_WITH_YOURSELF);

        ConversationMember requesterMember = getActiveMemberOrThrow(conversationId, requesterId);
        Set<AdminPermission> requestedAdminPermissions = request.permissions() != null ? request.permissions() : new HashSet<>();

        if (!permissionService.canManageAdminPermissions(requesterMember, requestedAdminPermissions)) {
            throw new BusinessException(ErrorCode.CANNOT_GRANT_UNPOSSESSED_PERMISSION);
        }

        ConversationMember targetMember = memberRepository.findById(new ConversationMemberId(conversationId, targetUserId))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (targetMember.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (targetMember.getRole() == ConversationRole.OWNER) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        if (targetMember.getRole() != ConversationRole.ADMIN) {
            targetMember.setRole(ConversationRole.ADMIN);
        }

        targetMember.setAdminPermissions(requestedAdminPermissions);
        memberRepository.save(targetMember);

        ConversationResponse updatedResponse = mapToConversationResponse(conversation, null);
        WsEnvelope<ConversationResponse> envelope = WsEnvelope.of("CONVERSATION_UPDATED", updatedResponse);
        broadcastEnvelopeToMembers(conversation, envelope, null);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getMemberPermissions(UUID requesterId, UUID conversationId, UUID targetUserId) {
        getActiveMemberOrThrow(conversationId, requesterId);
        ConversationMember targetMember = getActiveMemberOrThrow(conversationId, targetUserId);
        return mapToUserDto(targetMember);
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
                member.getRole() != null ? member.getRole().name() : null,
                member.getMemberPermissions(),
                member.getAdminPermissions()
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
                conv.isPublic(),
                conv.getLinkedDiscussionGroupId()
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

    private void broadcastMemberEventAndSystemMessage(
            Conversation conversation,
            User eventUser,
            String eventType,
            String systemText
    ) {
        if (conversation.getType() != ConversationType.CHANNEL) {
            Message systemMsg = Message.builder()
                    .conversation(conversation)
                    .sender(eventUser)
                    .messageType(MessageType.SYSTEM)
                    .body(systemText)
                    .build();
            systemMsg = messageRepository.save(systemMsg);

            ChatMessageResponse msgResponse = messageMapper.toResponse(systemMsg, List.of(), null);
            WsEnvelope<ChatMessageResponse> msgEnvelope = WsEnvelope.of("NEW_MESSAGE", msgResponse);
            broadcastEnvelopeToMembers(conversation, msgEnvelope, eventUser.getId());
        }

        MemberEventResponse memberData = new MemberEventResponse(
                conversation.getId(),
                eventUser.getId(),
                eventUser.getUsername(),
                eventUser.getDisplayName(),
                null
        );
        WsEnvelope<MemberEventResponse> eventEnvelope = WsEnvelope.of(eventType, memberData);
        broadcastEnvelopeToMembers(conversation, eventEnvelope, eventUser.getId());
    }

    private void broadcastEnvelopeToMembers(Conversation conversation, WsEnvelope<?> envelope, UUID eventUserId) {
        List<UUID> memberIds = getConversationMemberIds(conversation.getId());
        Set<UUID> targetIds = new HashSet<>(memberIds);
        if (eventUserId != null) {
            targetIds.add(eventUserId);
        }

        if (conversation.getType() == ConversationType.CHANNEL && targetIds.size() > 1000) {
            messagingTemplate.convertAndSend("/topic/channels/" + conversation.getId(), envelope);
        } else {
            for (UUID mId : targetIds) {
                messagingTemplate.convertAndSendToUser(mId.toString(), "/queue/chat", envelope);
            }
        }
    }

    @Override
    @Transactional
    public DiscussionGroupInfoResponse linkDiscussionGroup(UUID channelId, UUID groupId, UUID requesterId) {
        if (channelId.equals(groupId)) {
            throw new BusinessException(ErrorCode.CANNOT_LINK_SAME_CONVERSATION);
        }

        Conversation channelConv = getConversationOrThrow(channelId);
        Conversation groupConv = getConversationOrThrow(groupId);

        if (channelConv.getType() != ConversationType.CHANNEL || groupConv.getType() != ConversationType.GROUP) {
            throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TYPES);
        }

        ConversationMember channelMember = getActiveMemberOrThrow(channelId, requesterId);
        if (!permissionService.hasAdminPermission(channelMember, AdminPermission.CHANGE_INFO)) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_OF_BOTH_CONVERSATIONS);
        }

        ConversationMember groupMember = getActiveMemberOrThrow(groupId, requesterId);
        if (!permissionService.hasAdminPermission(groupMember, AdminPermission.CHANGE_INFO)) {
            throw new BusinessException(ErrorCode.NOT_ADMIN_OF_BOTH_CONVERSATIONS);
        }

        if (channelConv.getLinkedDiscussionGroupId() != null) {
            throw new BusinessException(ErrorCode.CHANNEL_ALREADY_HAS_DISCUSSION);
        }

        Optional<Conversation> existingLink = conversationRepository.findByLinkedDiscussionGroupId(groupId);
        if (existingLink.isPresent()) {
            throw new BusinessException(ErrorCode.GROUP_ALREADY_LINKED);
        }

        channelConv.setLinkedDiscussionGroupId(groupId);
        conversationRepository.save(channelConv);

        int memberCount = memberRepository.findByConversationIdAndLeftAtIsNull(groupId).size();
        String groupAvatarUrl = resolveMediaUrl(groupConv.getAvatarMediaId());

        return new DiscussionGroupInfoResponse(
                channelId,
                groupId,
                groupConv.getTitle(),
                groupAvatarUrl,
                memberCount
        );
    }

    @Override
    @Transactional
    public void unlinkDiscussionGroup(UUID channelId, UUID requesterId) {
        Conversation channelConv = getConversationOrThrow(channelId);
        if (channelConv.getType() != ConversationType.CHANNEL) {
            throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TYPES);
        }

        ConversationMember channelMember = getActiveMemberOrThrow(channelId, requesterId);
        if (!permissionService.hasAdminPermission(channelMember, AdminPermission.CHANGE_INFO)) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }

        if (channelConv.getLinkedDiscussionGroupId() == null) {
            throw new BusinessException(ErrorCode.DISCUSSION_NOT_LINKED);
        }

        channelConv.setLinkedDiscussionGroupId(null);
        conversationRepository.save(channelConv);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscussionGroupInfoResponse getLinkedDiscussionGroup(UUID conversationId, UUID requesterId) {
        Conversation conv = getConversationOrThrow(conversationId);
        Conversation channelConv;
        Conversation groupConv;

        if (conv.getType() == ConversationType.CHANNEL) {
            channelConv = conv;
            if (!channelConv.isPublic()) {
                getActiveMemberOrThrow(channelConv.getId(), requesterId);
            }
            UUID linkedGroupId = channelConv.getLinkedDiscussionGroupId();
            if (linkedGroupId == null) {
                throw new BusinessException(ErrorCode.DISCUSSION_NOT_LINKED);
            }
            groupConv = getConversationOrThrow(linkedGroupId);
        } else if (conv.getType() == ConversationType.GROUP) {
            groupConv = conv;
            if (!groupConv.isPublic()) {
                getActiveMemberOrThrow(groupConv.getId(), requesterId);
            }
            channelConv = conversationRepository.findByLinkedDiscussionGroupId(groupConv.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DISCUSSION_NOT_LINKED));
        } else {
            throw new BusinessException(ErrorCode.INVALID_CONVERSATION_TYPES);
        }

        int memberCount = memberRepository.findByConversationIdAndLeftAtIsNull(groupConv.getId()).size();
        String groupAvatarUrl = resolveMediaUrl(groupConv.getAvatarMediaId());

        return new DiscussionGroupInfoResponse(
                channelConv.getId(),
                groupConv.getId(),
                groupConv.getTitle(),
                groupAvatarUrl,
                memberCount
        );
    }
}