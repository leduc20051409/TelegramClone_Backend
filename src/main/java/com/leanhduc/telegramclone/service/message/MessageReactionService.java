package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.ToggleReactionResult;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.repository.MessageReactionRepository;
import com.leanhduc.telegramclone.repository.MessageRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageReactionService implements IMessageReactionService {

    private final MessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public ToggleReactionResult toggleReaction(Long messageId, UUID userId, String reaction) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        UUID conversationId = message.getConversation().getId();
        ConversationMemberId memberId = new ConversationMemberId(conversationId, userId);
        
        ConversationMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));
        if (member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }

        // Fetch all existing reactions by this user on this message
        List<MessageReaction> userReactions = reactionRepository.findByIdMessageIdAndIdUserId(messageId, userId);

        boolean alreadyHasSame = false;
        for (MessageReaction existing : userReactions) {
            if (existing.getId().getReaction().equals(reaction)) {
                alreadyHasSame = true;
                break;
            }
        }

        // Remove all previous reactions for this user on this message
        if (!userReactions.isEmpty()) {
            reactionRepository.deleteAll(userReactions);
            message.getReactions().removeAll(userReactions);
        }

        if (!alreadyHasSame) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            MessageReactionId reactionId = new MessageReactionId(messageId, userId, reaction);
            MessageReaction messageReaction = MessageReaction.builder()
                    .id(reactionId)
                    .message(message)
                    .user(user)
                    .build();

            reactionRepository.save(messageReaction);
            message.getReactions().add(messageReaction);
        }

        reactionRepository.flush();

        return new ToggleReactionResult(conversationId, messageMapper.mapReactions(message.getReactions()));
    }
}
