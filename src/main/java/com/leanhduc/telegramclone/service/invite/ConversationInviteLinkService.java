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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        return inviteLinkMapper.toResponse(inviteLink);
    }

    @Override
    public InviteLinkInfoResponse getInviteLinkInfo(String inviteCode) {
        ConversationInviteLink inviteLink = inviteLinkRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

        if (inviteLink.isRevoked()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_REVOKED);
        }

        if (inviteLink.getExpireAt() != null && inviteLink.getExpireAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVITE_LINK_EXPIRED);
        }

        if (inviteLink.getMemberLimit() > 0 && inviteLink.getCurrentUses() >= inviteLink.getMemberLimit()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_LIMIT_REACHED);
        }

        Conversation conversation = inviteLink.getConversation();
        String avatarUrl = null;
        if (conversation.getAvatarMediaId() != null) {
            avatarUrl = mediaRepository.findById(conversation.getAvatarMediaId())
                    .map(Media::getUrl)
                    .orElse(null);
        }

        int memberCount = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId()).size();

        return new InviteLinkInfoResponse(
                inviteLink.getInviteCode(),
                conversation.getId(),
                conversation.getTitle(),
                conversation.getDescription(),
                avatarUrl,
                memberCount
        );
    }

    @Override
    @Transactional
    public InviteLinkResponse joinConversation(UUID userId, String inviteCode) {
        // Retrieve invite link with pessimistic write lock to handle concurrent join transactions safely
        ConversationInviteLink inviteLink = inviteLinkRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND));

        if (inviteLink.isRevoked()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_REVOKED);
        }

        if (inviteLink.getExpireAt() != null && inviteLink.getExpireAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.INVITE_LINK_EXPIRED);
        }

        if (inviteLink.getMemberLimit() > 0 && inviteLink.getCurrentUses() >= inviteLink.getMemberLimit()) {
            throw new BusinessException(ErrorCode.INVITE_LINK_LIMIT_REACHED);
        }

        UUID conversationId = inviteLink.getConversation().getId();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if user is already an active member of this conversation
        Optional<ConversationMember> targetMemberOpt = memberRepository.findById(new ConversationMemberId(conversationId, userId));
        if (targetMemberOpt.isPresent()) {
            ConversationMember targetMember = targetMemberOpt.get();
            if (targetMember.getLeftAt() == null) {
                throw new BusinessException(ErrorCode.ALREADY_IN_CONVERSATION);
            }
            // Reactivate membership
            targetMember.setLeftAt(null);
            targetMember.setJoinedAt(Instant.now());
            targetMember.setRole(ConversationRole.MEMBER);
            memberRepository.save(targetMember);
        } else {
            // Create new membership
            ConversationMember newMember = ConversationMember.builder()
                    .id(new ConversationMemberId(conversationId, userId))
                    .conversation(inviteLink.getConversation())
                    .user(targetUser)
                    .role(ConversationRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build();
            memberRepository.save(newMember);
        }

        // Increment use count atomically within the pessimistic write lock transaction
        inviteLink.setCurrentUses(inviteLink.getCurrentUses() + 1);
        inviteLink = inviteLinkRepository.save(inviteLink);

        return inviteLinkMapper.toResponse(inviteLink);
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

        return inviteLinkMapper.toResponse(inviteLink);
    }

    @Override
    public List<InviteLinkResponse> getInviteLinksForConversation(UUID requesterId, UUID conversationId) {
        // Check if requester is owner/admin
        checkAdminOrOwnerPermission(requesterId, conversationId);

        List<ConversationInviteLink> links = inviteLinkRepository.findAllByConversationIdAndIsRevokedFalse(conversationId);
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
}
