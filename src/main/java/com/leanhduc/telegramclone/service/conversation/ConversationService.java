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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService implements IConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;

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
            return new ConversationResponse(
                    conv.getId(),
                    conv.getType(),
                    conv.getTitle(),
                    conv.getCreatedAt(),
                    lastMsg != null ? lastMsg.getBody() : null,
                    lastMsg != null ? lastMsg.getCreatedAt() : null,
                    targetUserId
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

        return new ConversationResponse(
                newConversation.getId(),
                newConversation.getType(),
                newConversation.getTitle(),
                newConversation.getCreatedAt(),
                null,
                null,
                targetUserId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getConversationMemberIds(UUID conversationId) {
        return memberRepository.findByConversationId(conversationId).stream()
                .map(member -> member.getUser().getId())
                .toList();
    }

    @Override
    public List<ConversationResponse> getAllConversationsByUser(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllByMember(userId);
        return conversations.stream()
                .map(conv -> {
                    UUID partnerId = memberRepository.findByConversationId(conv.getId()).stream()
                            .map(member -> member.getUser().getId())
                            .filter(id -> !id.equals(userId))
                            .findFirst()
                            .orElse(null);

                    List<Message> latestMessages = messageRepository.findByConversationIdAndDeletedFalseOrderByIdDesc(
                            conv.getId(), PageRequest.of(0, 1)
                    );
                    Message lastMsg = latestMessages.isEmpty() ? null : latestMessages.get(0);

                    return new ConversationResponse(
                            conv.getId(),
                            conv.getType(),
                            conv.getTitle(),
                            conv.getCreatedAt(),
                            lastMsg != null ? lastMsg.getBody() : null,
                            lastMsg != null ? lastMsg.getCreatedAt() : null,
                            partnerId
                    );
                })
                .toList();
    }
}