package com.leanhduc.telegramclone.service.broadcast;

import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChatBroadcaster implements ChatBroadcaster {

    private static final int LARGE_CHANNEL_THRESHOLD = 1000;

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationMemberRepository memberRepository;

    @Override
    public void broadcast(UUID conversationId, ConversationType type, WsEnvelope<?> envelope) {
        deliver(conversationId, type, null, null, envelope);
    }

    @Override
    public void broadcastExcept(UUID conversationId, ConversationType type, UUID excludedUserId, WsEnvelope<?> envelope) {
        deliver(conversationId, type, excludedUserId, null, envelope);
    }

    @Override
    public void broadcastIncluding(UUID conversationId, ConversationType type, UUID additionalUserId, WsEnvelope<?> envelope) {
        deliver(conversationId, type, null, additionalUserId, envelope);
    }

    private void deliver(
            UUID conversationId,
            ConversationType type,
            UUID excludedUserId,
            UUID additionalUserId,
            WsEnvelope<?> envelope
    ) {
        if (conversationId == null || envelope == null) {
            return;
        }

        if (type == ConversationType.CHANNEL) {
            long memberCount = memberRepository.countByConversationIdAndLeftAtIsNull(conversationId);
            if (memberCount > LARGE_CHANNEL_THRESHOLD) {
                messagingTemplate.convertAndSend("/topic/channels/" + conversationId, envelope);
                if (additionalUserId != null && !additionalUserId.equals(excludedUserId)) {
                    messagingTemplate.convertAndSendToUser(additionalUserId.toString(), "/queue/chat", envelope);
                }
                return;
            }
        }

        List<UUID> activeUserIds = memberRepository.findActiveUserIdsByConversationId(conversationId);
        Set<UUID> recipients = new LinkedHashSet<>(activeUserIds);
        if (additionalUserId != null) {
            recipients.add(additionalUserId);
        }

        for (UUID memberId : recipients) {
            if (excludedUserId != null && memberId.equals(excludedUserId)) {
                continue;
            }
            messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/chat", envelope);
        }
    }
}
