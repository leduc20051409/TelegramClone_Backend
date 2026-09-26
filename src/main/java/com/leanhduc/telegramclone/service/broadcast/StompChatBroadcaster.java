package com.leanhduc.telegramclone.service.broadcast;

import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
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
        broadcastExcept(conversationId, type, null, envelope);
    }

    @Override
    public void broadcastExcept(UUID conversationId, ConversationType type, UUID excludedUserId, WsEnvelope<?> envelope) {
        if (conversationId == null || envelope == null) {
            return;
        }

        if (type == ConversationType.CHANNEL) {
            long memberCount = memberRepository.countByConversationIdAndLeftAtIsNull(conversationId);
            if (memberCount > LARGE_CHANNEL_THRESHOLD) {
                messagingTemplate.convertAndSend("/topic/channels/" + conversationId, envelope);
                return;
            }
        }

        List<UUID> memberIds = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId)
                .stream()
                .map(m -> m.getId() != null ? m.getId().getUserId() : m.getUser().getId())
                .toList();

        for (UUID memberId : memberIds) {
            if (excludedUserId != null && memberId.equals(excludedUserId)) {
                continue;
            }
            messagingTemplate.convertAndSendToUser(memberId.toString(), "/queue/chat", envelope);
        }
    }
}
