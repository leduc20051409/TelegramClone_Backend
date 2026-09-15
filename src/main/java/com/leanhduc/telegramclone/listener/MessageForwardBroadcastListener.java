package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageForwardBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessagesForwarded(MessagesForwardedEvent event) {
        if (event == null || event.broadcasts() == null) {
            return;
        }

        for (MessagesForwardedEvent.TargetBroadcastDto broadcast : event.broadcasts()) {
            WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("NEW_MESSAGE", broadcast.messageResponse());

            if (broadcast.conversationType() == ConversationType.CHANNEL && broadcast.memberIds().size() > 1000) {
                messagingTemplate.convertAndSend(
                        "/topic/channels/" + broadcast.conversationId(),
                        envelope
                );
            } else {
                for (UUID memberId : broadcast.memberIds()) {
                    messagingTemplate.convertAndSendToUser(
                            memberId.toString(),
                            "/queue/chat",
                            envelope
                    );
                }
            }
        }
    }
}
