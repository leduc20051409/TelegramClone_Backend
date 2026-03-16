package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.Message;
import com.leanhduc.telegramclone.model.User;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.MessageRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService implements IMessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public ChatMessageResponse saveMessage(UUID senderId, ChatMessageRequest request) {
        boolean isMember = memberRepository.existsByConversationIdAndUserId(request.conversationId(), senderId);
        if (!isMember) {
            throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
        }
        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND)); // Thêm lỗi này vào ErrorCode
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .body(request.message())
                .messageType(MessageType.TEXT)
                .deleted(false)
                .build();

        message = messageRepository.save(message);

        return messageMapper.toResponse(message);
    }

    @Override
    public List<ChatMessageResponse> getMessageHistory(UUID conversationId, UUID currentUserId, Long cursor, int size) {
        boolean isMember = memberRepository.existsByConversationIdAndUserId(conversationId, currentUserId);
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
        return messages.stream()
                .map(messageMapper::toResponse)
                .toList();
    }
}