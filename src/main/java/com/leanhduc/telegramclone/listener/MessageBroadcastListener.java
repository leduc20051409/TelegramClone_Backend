package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageEdited(MessageEditedEvent event) {
        if (event == null || event.updatedMessage() == null) {
            return;
        }

        ChatMessageResponse response = event.updatedMessage();
        WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("MESSAGE_EDITED", response);
        broadcastEnvelope(response.conversationId(), event.conversationType(), event.memberIds(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageDeleted(MessageDeletedEvent event) {
        if (event == null || event.messageId() == null || event.conversationId() == null) {
            return;
        }

        DeleteMessageResponse payload = new DeleteMessageResponse(event.messageId(), event.conversationId());
        WsEnvelope<DeleteMessageResponse> envelope = WsEnvelope.of("MESSAGE_DELETED", payload);
        broadcastEnvelope(event.conversationId(), event.conversationType(), event.memberIds(), envelope);
    }

    private <T> void broadcastEnvelope(UUID conversationId, ConversationType conversationType, List<UUID> memberIds, WsEnvelope<T> envelope) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }

        if (conversationType == ConversationType.CHANNEL && memberIds.size() > 1000) {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + conversationId,
                    envelope
            );
        } else {
            for (UUID memberId : memberIds) {
                messagingTemplate.convertAndSendToUser(
                        memberId.toString(),
                        "/queue/chat",
                        envelope
                );
            }
        }
    }
}
