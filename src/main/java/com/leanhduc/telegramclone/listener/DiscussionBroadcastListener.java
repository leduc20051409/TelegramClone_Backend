package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;
import com.leanhduc.telegramclone.event.DiscussionBroadcastEvent;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiscussionBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDiscussionBroadcast(DiscussionBroadcastEvent event) {

        if (event.groupRootResponse() != null && event.groupMemberIds() != null) {
            WsEnvelope<ChatMessageResponse> groupEnvelope =
                    WsEnvelope.of("NEW_MESSAGE", event.groupRootResponse());

            for (UUID memberId : event.groupMemberIds()) {
                messagingTemplate.convertAndSendToUser(
                        memberId.toString(),
                        "/queue/chat",
                        groupEnvelope
                );
            }
        }

        if (event.commentCountUpdate() != null) {
            WsEnvelope<CommentCountUpdateDto> countEnvelope =
                    WsEnvelope.of("COMMENT_COUNT_UPDATED", event.commentCountUpdate());

            if (event.channelConvId() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/channels/" + event.channelConvId(),
                        countEnvelope
                );
            }

            if (event.commentGroupMemberIds() != null) {
                for (UUID memberId : event.commentGroupMemberIds()) {
                    messagingTemplate.convertAndSendToUser(
                            memberId.toString(),
                            "/queue/chat",
                            countEnvelope
                    );
                }
            }
        }
    }
}
