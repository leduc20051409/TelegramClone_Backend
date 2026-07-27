package com.leanhduc.telegramclone.service.invite;

import com.leanhduc.telegramclone.dto.invite.CreateInviteLinkRequest;
import com.leanhduc.telegramclone.dto.invite.InviteLinkInfoResponse;
import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.InviteLinkMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.MemberEventResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.enums.MessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationInviteLinkService implements IConversationInviteLinkService {

    private final ConversationInviteLinkRepository inviteLinkRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final InviteLinkMapper inviteLinkMapper;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public InviteLinkResponse createInviteLink(UUID requesterId, UUID conversationId, CreateInviteLinkRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getType() == ConversationType.PRIVATE) {
            throw new BusinessException(ErrorCode.CANNOT_CHAT_WITH_YOURSELF);
        }

        // Check if requester is owner/admin
        checkAdminOrOwnerPermission(requesterId, conversationId);

        User creator = userRepository.findById(requesterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean isPrimary = request.isPrimary() != null && request.isPrimary();

        if (isPrimary) {
            // Demote any existing primary links in the conversation
            inviteLinkRepository.demotePrimaryLinks(conversationId);
        }

        String inviteCode = generateUniqueInviteCode();

        ConversationInviteLink inviteLink = ConversationInviteLink.builder()
                .conversation(conversation)
                .createdBy(creator)
                .inviteCode(inviteCode)
                .name(request.name())
                .expireAt(request.expireAt())
                .memberLimit(request.memberLimit() != null ? request.memberLimit() : 0)
                .isPrimary(isPrimary)
                .currentUses(0)
                .isRevoked(false)
                .build();

        inviteLink = inviteLinkRepository.save(inviteLink);
        InviteLinkResponse response = inviteLinkMapper.toResponse(inviteLink);
        broadcastInviteLinkUpdated(conversationId, response);
        return response;
    }

    private record ResolvedInviteTarget(
            Conversation conversation,
            ConversationInviteLink inviteLink,
            boolean isPublicUsernameFlow
    ) {}

    private ResolvedInviteTarget resolveByCode(String code, boolean forUpdate) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND);
        }

        String trimmedCode = code.trim();

        // 1. Try finding by invite code (case-sensitive)
        Optional<ConversationInviteLink> inviteLinkOpt = forUpdate
                ? inviteLinkRepository.findByInviteCodeForUpdate(trimmedCode)
                : inviteLinkRepository.findByInviteCode(trimmedCode);

        if (inviteLinkOpt.isPresent()) {
            ConversationInviteLink link = inviteLinkOpt.get();
            return new ResolvedInviteTarget(link.getConversation(), link, false);
        }

        // 2. Fallback to public conversation username lookup (case-insensitive)
        Optional<Conversation> convOpt = conversationRepository.findByUsernameIgnoreCase(trimmedCode);
        if (convOpt.isPresent()) {
            Conversation conv = convOpt.get();
            if (conv.isPublic() && conv.getUsername() != null) {
                return new ResolvedInviteTarget(conv, null, true);
            }
        }

        throw new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND);
    }

    @Override
    public InviteLinkInfoResponse getInviteLinkInfo(String inviteCode) {
        ResolvedInviteTarget target = resolveByCode(inviteCode, false);

        if (!target.isPublicUsernameFlow()) {
            ConversationInviteLink inviteLink = target.inviteLink();

            if (inviteLink.isRevoked()) {
                throw new BusinessException(ErrorCode.INVITE_LINK_REVOKED);
            }

            if (inviteLink.getExpireAt() != null && inviteLink.getExpireAt().isBefore(Instant.now())) {
                throw new BusinessException(ErrorCode.INVITE_LINK_EXPIRED);
            }

            if (inviteLink.getMemberLimit() > 0 && inviteLink.getCurrentUses() >= inviteLink.getMemberLimit()) {
                throw new BusinessException(ErrorCode.INVITE_LINK_LIMIT_REACHED);
            }
        }

        Conversation conversation = target.conversation();
        String avatarUrl = null;
        if (conversation.getAvatarMediaId() != null) {
            avatarUrl = mediaRepository.findById(conversation.getAvatarMediaId())
                    .map(Media::getUrl)
                    .orElse(null);
        }

        int memberCount = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).size();

        String codeToReturn = target.isPublicUsernameFlow()
                ? conversation.getUsername()
                : target.inviteLink().getInviteCode();

        return new InviteLinkInfoResponse(
                codeToReturn,
                conversation.getId(),
                conversation.getTitle(),
                conversation.getDescription(),
                avatarUrl,
                memberCount,
                conversation.getType().name());
    }

    @Override
    @Transactional
    public InviteLinkResponse joinConversation(UUID userId, String inviteCode) {
        ResolvedInviteTarget target = resolveByCode(inviteCode, true);

        if (!target.isPublicUsernameFlow()) {
            ConversationInviteLink inviteLink = target.inviteLink();

            if (inviteLink.isRevoked()) {
                throw new BusinessException(ErrorCode.INVITE_LINK_REVOKED);
            }

            if (inviteLink.getExpireAt() != null && inviteLink.getExpireAt().isBefore(Instant.now())) {
                throw new BusinessException(ErrorCode.INVITE_LINK_EXPIRED);
            }

            if (inviteLink.getMemberLimit() > 0 && inviteLink.getCurrentUses() >= inviteLink.getMemberLimit()) {
                throw new BusinessException(ErrorCode.INVITE_LINK_LIMIT_REACHED);
            }
        }

        Conversation conversation = target.conversation();
        UUID conversationId = conversation.getId();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if user is already an active member of this conversation
        Optional<ConversationMember> targetMemberOpt = memberRepository
                .findById(new ConversationMemberId(conversationId, userId));

        if (targetMemberOpt.isPresent()) {
            ConversationMember targetMember = targetMemberOpt.get();
            if (targetMember.getLeftAt() == null) {
                // If user is already an active member, return success response directly to navigate to chat
                if (target.isPublicUsernameFlow()) {
                    return new InviteLinkResponse(
                            0L,
                            conversationId,
                            conversation.getUsername(),
                            conversation.getTitle(),
                            null,
                            0,
                            0,
                            false,
                            true,
                            conversation.getCreatedBy(),
                            conversation.getCreatedAt(),
                            Instant.now()
                    );
                } else {
                    return inviteLinkMapper.toResponse(target.inviteLink());
                }
            }
            // Reactivate membership if user previously left
            targetMember.setLeftAt(null);
            targetMember.setJoinedAt(Instant.now());
            targetMember.setRole(ConversationRole.MEMBER);
            memberRepository.save(targetMember);
        } else {
            // Create new membership
            ConversationMember newMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, userId))
                    .conversation(conversation)
                    .user(targetUser)
                    .role(ConversationRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build();
            memberRepository.save(newMember);
        }

        InviteLinkResponse response;
        if (target.isPublicUsernameFlow()) {
            response = new InviteLinkResponse(
                    0L,
                    conversationId,
                    conversation.getUsername(),
                    conversation.getTitle(),
                    null,
                    0,
                    0,
                    false,
                    true,
                    conversation.getCreatedBy(),
                    conversation.getCreatedAt(),
                    Instant.now()
            );
        } else {
            // Increment use count atomically within the pessimistic write lock transaction
            ConversationInviteLink inviteLink = target.inviteLink();
            inviteLink.setCurrentUses(inviteLink.getCurrentUses() + 1);
            inviteLink = inviteLinkRepository.save(inviteLink);
            response = inviteLinkMapper.toResponse(inviteLink);
            broadcastInviteLinkUpdated(conversationId, response);
        }

        broadcastJoinEvents(conversation, targetUser, target.isPublicUsernameFlow() ? null : inviteCode);
        return response;
    }

    @Override
    @Transactional
    public InviteLinkResponse revokeInviteLink(UUID requesterId, Long inviteLinkId) {
        ConversationInviteLink inviteLink = inviteLinkRepository.findById(inviteLinkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

        // Check if requester is owner/admin
        checkAdminOrOwnerPermission(requesterId, inviteLink.getConversation().getId());

        inviteLink.setRevoked(true);
        inviteLink = inviteLinkRepository.save(inviteLink);

        InviteLinkResponse response = inviteLinkMapper.toResponse(inviteLink);
        broadcastInviteLinkUpdated(inviteLink.getConversation().getId(), response);
        return response;
    }

    @Override
    public List<InviteLinkResponse> getInviteLinksForConversation(UUID requesterId, UUID conversationId) {
        // Verify requester is in the conversation and active
        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        List<ConversationInviteLink> links = inviteLinkRepository
                .findAllByConversationIdAndIsRevokedFalse(conversationId);

        // If requester is not owner/admin, only return the primary link(s)
        if (member.getRole() != ConversationRole.OWNER && member.getRole() != ConversationRole.ADMIN) {
            return links.stream()
                    .filter(ConversationInviteLink::isPrimary)
                    .map(inviteLinkMapper::toResponse)
                    .toList();
        }

        return links.stream()
                .map(inviteLinkMapper::toResponse)
                .toList();
    }

    private void checkAdminOrOwnerPermission(UUID requesterId, UUID conversationId) {
        ConversationMember member = memberRepository.findById(new ConversationMemberId(conversationId, requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
        if (member.getRole() != ConversationRole.OWNER && member.getRole() != ConversationRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateRandomAlphanumeric(16 + secureRandom.nextInt(7)); // 16 to 22 characters
        } while (inviteLinkRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private String generateRandomAlphanumeric(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void broadcastInviteLinkUpdated(UUID conversationId, InviteLinkResponse response) {
        WsEnvelope<InviteLinkResponse> envelope = WsEnvelope.of("INVITE_LINK_UPDATED", response);
        List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(m -> m.getUser().getId())
                .toList();
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/chat", envelope);
        }
    }

    private void broadcastJoinEvents(Conversation conversation, User targetUser, String inviteCode) {
        String displayName = targetUser.getDisplayName() != null && !targetUser.getDisplayName().isBlank()
                ? targetUser.getDisplayName() : targetUser.getUsername();
        String systemText = inviteCode != null
                ? displayName + " joined the group via invite link"
                : displayName + " joined the group";

        Message systemMsg = Message.builder()
                .conversation(conversation)
                .sender(targetUser)
                .messageType(MessageType.SYSTEM)
                .body(systemText)
                .build();
        systemMsg = messageRepository.save(systemMsg);

        ChatMessageResponse msgResponse = messageMapper.toResponse(systemMsg, List.of(), null);
        WsEnvelope<ChatMessageResponse> msgEnvelope = WsEnvelope.of("NEW_MESSAGE", msgResponse);

        MemberEventResponse memberData = new MemberEventResponse(
                conversation.getId(),
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getDisplayName(),
                null
        );
        WsEnvelope<MemberEventResponse> eventEnvelope = WsEnvelope.of("MEMBER_JOINED", memberData);

        List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).stream()
                .map(m -> m.getUser().getId())
                .toList();
        Set<UUID> targetIds = new HashSet<>(memberIds);
        targetIds.add(targetUser.getId());

        if (conversation.getType() == ConversationType.CHANNEL && targetIds.size() > 1000) {
            messagingTemplate.convertAndSend("/topic/channels/" + conversation.getId(), msgEnvelope);
            messagingTemplate.convertAndSend("/topic/channels/" + conversation.getId(), eventEnvelope);
        } else {
            for (UUID mId : targetIds) {
                messagingTemplate.convertAndSendToUser(mId.toString(), "/queue/chat", msgEnvelope);
                messagingTemplate.convertAndSendToUser(mId.toString(), "/queue/chat", eventEnvelope);
            }
        }
    }
}
