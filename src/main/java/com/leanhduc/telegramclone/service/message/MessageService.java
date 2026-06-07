package com.leanhduc.telegramclone.service.message;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.message.EditMessageRequest;
import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService implements IMessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UnreadCounterRepository unreadCounterRepository;
    private final MediaRepository mediaRepository;
    private final MessageMediaRepository messageMediaRepository;
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

        List<UUID> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        MessageType messageType = mediaIds.isEmpty() ? MessageType.TEXT : MessageType.FILE;
        Map<UUID, Media> mediaById = Collections.emptyMap();
        if (!mediaIds.isEmpty()) {
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

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .body(request.message())
                .messageType(messageType)
                .deleted(false)
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
        return messageMapper.toResponse(message, mediaDtos);
    }

    @Override
    @Transactional(readOnly = true)
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
        return toResponsesWithMedia(messages);
    }

    @Override
    public void markMessagesAsRead(UUID currentUserId, ChatReadRequest request) {
        if (!memberRepository.existsByConversationIdAndUserId(request.conversationId(), currentUserId)) {
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
        if(counter.getLastReadMessageId() == null || counter.getLastReadMessageId() < request.lastReadMessageId()) {
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

        List<ChatMessageResponse> responses = new ArrayList<>(messages.size());
        for (Message message : messages) {
            List<MessageMedia> attachments = mediaByMessageId.getOrDefault(message.getId(), List.of());
            List<MediaAttachmentDto> mediaDtos = attachments.stream()
                    .map(MessageMedia::getMedia)
                    .map(this::toMediaDto)
                    .toList();
            responses.add(messageMapper.toResponse(message, mediaDtos));
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
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        message.setBody(request.message());
        message.setEdited(true);
        message.setUpdatedAt(java.time.Instant.now());

        List<UUID> mediaIds = request.mediaIds();
        if (mediaIds != null) {
            // Remove old message-media relationships
            messageMediaRepository.deleteByMessageId(messageId);

            if (!mediaIds.isEmpty()) {
                Set<UUID> uniqueMediaIds = new java.util.HashSet<>(mediaIds);
                if (uniqueMediaIds.size() != mediaIds.size()) {
                    throw new BusinessException(ErrorCode.DUPLICATE_MEDIA);
                }
                List<Media> mediaList = mediaRepository.findByIdInAndOwnerId(new java.util.ArrayList<>(uniqueMediaIds), currentUserId);
                if (mediaList.size() != uniqueMediaIds.size()) {
                    throw new BusinessException(ErrorCode.MEDIA_NOT_ACCESSIBLE);
                }
                Map<UUID, Media> mediaById = mediaList.stream()
                        .collect(Collectors.toMap(Media::getId, media -> media));

                List<MessageMedia> messageMediaList = new java.util.ArrayList<>();
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

        return messageMapper.toResponse(message, mediaDtos);
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
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MESSAGE_ACTION);
        }

        message.setDeleted(true);
        messageRepository.save(message);

        return message.getConversation().getId();
    }
}
