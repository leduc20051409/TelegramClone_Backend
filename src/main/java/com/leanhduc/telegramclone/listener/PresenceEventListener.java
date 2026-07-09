package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.repository.ContactRepository;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.service.Presence.UserPresenceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final ConversationMemberRepository conversationMemberRepository;
    private final ContactRepository contactRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public record UserPresenceDto(
            UUID userId,
            boolean online,
            Long lastSeen
    ) {}

    @EventListener
    public void handleUserPresenceChanged(UserPresenceChangedEvent event) {
        UUID userId = event.getUserId();
        boolean online = event.isOnline();
        Long lastSeenMillis = event.getLastSeen() != null ? event.getLastSeen().toEpochMilli() : null;

        log.info("Presence changed for user {}: online={}, lastSeen={}", userId, online, event.getLastSeen());

        // Get related users:
        // 1. Users who share conversations with this user
        List<UUID> sharedConversationMembers = conversationMemberRepository.findRelatedUserIds(userId);

        // 2. Users who added this user as a contact (and haven't blocked them)
        List<UUID> contactOwners = contactRepository.findOwnerIdsByContactIdAndBlockedFalse(userId);

        // Union both lists to avoid duplicate notifications
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
        WsEnvelope<UserPresenceDto> envelope = WsEnvelope.of("USER_PRESENCE_CHANGED", payload);

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
