package com.leanhduc.telegramclone.service.message.discussion;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;
import com.leanhduc.telegramclone.dto.message.DiscussionBroadcastEvent;
import com.leanhduc.telegramclone.dto.message.DiscussionMediaContext;
import com.leanhduc.telegramclone.mapper.MessageMapper;
import com.leanhduc.telegramclone.model.*;
import com.leanhduc.telegramclone.model.enums.MessageType;
import com.leanhduc.telegramclone.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DiscussionService implements IDiscussionService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageMediaRepository messageMediaRepository;
    private final DiscussionThreadLinkRepository discussionThreadLinkRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Integer handleChannelPost(Message channelMessage, DiscussionMediaContext mediaContext) {
        UUID linkedGroupId = channelMessage.getConversation().getLinkedDiscussionGroupId();
        if (linkedGroupId == null) {
            return null;
        }

        Conversation linkedGroup = conversationRepository.findById(linkedGroupId).orElse(null);
        if (linkedGroup == null) {
            return null;
        }

        Message groupRootMessage = Message.builder()
                .conversation(linkedGroup)
                .sender(channelMessage.getSender())
                .body(channelMessage.getBody())
                .messageType(channelMessage.getMessageType())
                .deleted(false)
                .forwardedFromConversation(channelMessage.getConversation())
                .forwardedFromUser(channelMessage.getSender())
                .forwardedAt(java.time.Instant.now())
                .build();
        groupRootMessage = messageRepository.save(groupRootMessage);

        if (mediaContext.hasMedia()) {
            List<MessageMedia> groupMediaList = new ArrayList<>();
            List<UUID> mediaIds = mediaContext.mediaIds();
            for (int i = 0; i < mediaIds.size(); i++) {
                UUID mediaId = mediaIds.get(i);
                Media media = mediaContext.mediaById().get(mediaId);
                MessageMediaId mmId = new MessageMediaId(groupRootMessage.getId(), mediaId);
                MessageMedia groupMedia = MessageMedia.builder()
                        .id(mmId)
                        .message(groupRootMessage)
                        .media(media)
                        .ordinal(i)
                        .build();
                groupMediaList.add(groupMedia);
            }
            messageMediaRepository.saveAll(groupMediaList);
        }

        DiscussionThreadLink threadLink = DiscussionThreadLink.builder()
                .channelPostMessage(channelMessage)
                .groupRootMessage(groupRootMessage)
                .commentCount(0)
                .build();
        discussionThreadLinkRepository.save(threadLink);

        ChatMessageResponse groupRootResponse = messageMapper.toResponse(
                groupRootMessage, mediaContext.mediaDtos(), null, null);

        List<UUID> groupMemberIds = memberRepository
                .findByConversationIdAndLeftAtIsNull(linkedGroup.getId())
                .stream()
                .map(gm -> gm.getUser().getId())
                .toList();

        eventPublisher.publishEvent(new DiscussionBroadcastEvent(
                groupRootResponse, groupMemberIds, null, null, null));

        return 0;
    }

    @Override
    public void handleCommentCreated(Message comment) {
        Message replyTo = comment.getReplyTo();
        if (replyTo == null) {
            return;
        }

        DiscussionThreadLink threadLink = findThreadLinkByMessage(replyTo);
        if (threadLink == null) {
            return;
        }

        Long channelPostId = threadLink.getChannelPostMessage() != null
                ? threadLink.getChannelPostMessage().getId() : null;
        UUID channelConvId = (threadLink.getChannelPostMessage() != null
                && threadLink.getChannelPostMessage().getConversation() != null)
                ? threadLink.getChannelPostMessage().getConversation().getId() : null;
        Long groupRootId = threadLink.getGroupRootMessage() != null
                ? threadLink.getGroupRootMessage().getId() : null;

        discussionThreadLinkRepository.incrementCommentCount(threadLink.getId());
        int updatedCount = discussionThreadLinkRepository.getCommentCount(threadLink.getId());

        CommentCountUpdateDto updateDto = new CommentCountUpdateDto(
                channelPostId,
                channelConvId,
                groupRootId,
                comment.getConversation().getId(),
                updatedCount
        );

        List<UUID> commentGroupMemberIds = memberRepository
                .findByConversationIdAndLeftAtIsNull(comment.getConversation().getId())
                .stream()
                .map(gm -> gm.getUser().getId())
                .toList();

        eventPublisher.publishEvent(new DiscussionBroadcastEvent(
                null, null, updateDto, channelConvId, commentGroupMemberIds));
    }

    private DiscussionThreadLink findThreadLinkByMessage(Message msg) {
        Message current = msg;
        while (current != null) {
            Optional<DiscussionThreadLink> linkOpt =
                    discussionThreadLinkRepository.findByGroupRootMessageId(current.getId());
            if (linkOpt.isPresent()) {
                return linkOpt.get();
            }
            current = current.getReplyTo();
        }
        return null;
    }
}
