package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.MessagesForwardedEvent;
import com.leanhduc.telegramclone.service.broadcast.ChatBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageForwardBroadcastListener {

    private final ChatBroadcaster chatBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessagesForwarded(MessagesForwardedEvent event) {
        if (event == null || event.broadcasts() == null) {
            return;
        }

        for (MessagesForwardedEvent.TargetBroadcastDto broadcast : event.broadcasts()) {
            WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("NEW_MESSAGE", broadcast.messageResponse());
            chatBroadcaster.broadcast(broadcast.conversationId(), broadcast.conversationType(), envelope);
        }
    }
}
