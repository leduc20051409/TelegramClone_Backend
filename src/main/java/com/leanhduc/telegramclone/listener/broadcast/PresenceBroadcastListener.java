package com.leanhduc.telegramclone.listener.broadcast;

import com.leanhduc.telegramclone.dto.websocket.UserPresenceDto;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.UserPresenceChangedEvent;
import com.leanhduc.telegramclone.model.enums.WsEventType;
import com.leanhduc.telegramclone.repository.ContactRepository;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceBroadcastListener {

    private final ConversationMemberRepository conversationMemberRepository;
    private final ContactRepository contactRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleUserPresenceChanged(UserPresenceChangedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }

        UUID userId = event.userId();
        boolean online = event.online();
        Long lastSeenMillis = event.lastSeen() != null ? event.lastSeen().toEpochMilli() : null;

        log.info("Presence changed for user {}: online={}, lastSeen={}", userId, online, event.lastSeen());

        List<UUID> sharedConversationMembers = conversationMemberRepository.findRelatedUserIds(userId);
        List<UUID> contactOwners = contactRepository.findOwnerIdsByContactIdAndBlockedFalse(userId);

        Set<UUID> recipients = new HashSet<>();
        if (sharedConversationMembers != null) {
            recipients.addAll(sharedConversationMembers);
        }
        if (contactOwners != null) {
            recipients.addAll(contactOwners);
        }

        if (recipients.isEmpty()) {
            return;
        }

        UserPresenceDto payload = new UserPresenceDto(userId, online, lastSeenMillis);
        WsEnvelope<UserPresenceDto> envelope = WsEnvelope.of(WsEventType.USER_PRESENCE_CHANGED, payload);

        log.debug("Broadcasting presence update of user {} to {} recipients", userId, recipients.size());

        for (UUID recipientId : recipients) {
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/chat",
                    envelope
            );
        }
    }
}
