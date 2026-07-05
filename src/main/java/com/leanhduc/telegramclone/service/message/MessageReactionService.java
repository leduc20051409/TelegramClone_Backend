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

        // Find existing reaction by this user on this message inside the collection
        MessageReaction existingReaction = message.getReactions().stream()
                .filter(r -> r.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);

        if (existingReaction != null) {
            // Remove the old reaction from collection
            message.getReactions().remove(existingReaction);
        }

        // If it's a new reaction or a different emoji, add the new one
        if (existingReaction == null || !existingReaction.getId().getReaction().equals(reaction)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            MessageReactionId reactionId = new MessageReactionId(messageId, userId, reaction);
            MessageReaction newReaction = MessageReaction.builder()
                    .id(reactionId)
                    .message(message)
                    .user(user)
                    .build();
            message.getReactions().add(newReaction);
        }

        messageRepository.saveAndFlush(message);

        return new ToggleReactionResult(conversationId, messageMapper.mapReactions(message.getReactions()));
    }
}
