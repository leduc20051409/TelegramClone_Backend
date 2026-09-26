package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.DiscussionBroadcastEvent;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.service.broadcast.ChatBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DiscussionBroadcastListener {

    private final ChatBroadcaster chatBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDiscussionBroadcast(DiscussionBroadcastEvent event) {
        if (event == null) {
            return;
        }

        if (event.groupRootResponse() != null) {
            WsEnvelope<ChatMessageResponse> groupEnvelope =
                    WsEnvelope.of("NEW_MESSAGE", event.groupRootResponse());
            chatBroadcaster.broadcast(event.groupRootResponse().conversationId(), ConversationType.GROUP, groupEnvelope);
        }

        if (event.commentCountUpdate() != null) {
            WsEnvelope<CommentCountUpdateDto> countEnvelope =
                    WsEnvelope.of("COMMENT_COUNT_UPDATED", event.commentCountUpdate());

            if (event.channelConvId() != null) {
                chatBroadcaster.broadcast(event.channelConvId(), ConversationType.CHANNEL, countEnvelope);
            }

            if (event.commentCountUpdate().groupId() != null) {
                chatBroadcaster.broadcast(event.commentCountUpdate().groupId(), ConversationType.GROUP, countEnvelope);
            }
        }
    }
}
