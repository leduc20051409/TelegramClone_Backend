package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.MessageReactionEventDto;
import com.leanhduc.telegramclone.dto.websocket.UnpinMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.MessageCreatedEvent;
import com.leanhduc.telegramclone.event.MessageDeletedEvent;
import com.leanhduc.telegramclone.event.MessageEditedEvent;
import com.leanhduc.telegramclone.event.MessagePinnedEvent;
import com.leanhduc.telegramclone.event.MessageReactionChangedEvent;
import com.leanhduc.telegramclone.event.MessageUnpinnedEvent;
import com.leanhduc.telegramclone.event.MessagesReadEvent;
import com.leanhduc.telegramclone.service.broadcast.ChatBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcastListener {

    private final ChatBroadcaster chatBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageCreated(MessageCreatedEvent event) {
        if (event == null || event.message() == null) {
            return;
        }

        ChatMessageResponse response = event.message();
        WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("NEW_MESSAGE", response);
        chatBroadcaster.broadcast(response.conversationId(), event.conversationType(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessagesRead(MessagesReadEvent event) {
        if (event == null || event.conversationId() == null || event.readerId() == null) {
            return;
        }

        WsEnvelope<ChatReadRequest> envelope = WsEnvelope.of(
                "MESSAGES_READ",
                new ChatReadRequest(event.conversationId(), event.lastReadMessageId())
        );
        chatBroadcaster.broadcastExcept(event.conversationId(), event.conversationType(), event.readerId(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageEdited(MessageEditedEvent event) {
        if (event == null || event.updatedMessage() == null) {
            return;
        }

        ChatMessageResponse response = event.updatedMessage();
        WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("MESSAGE_EDITED", response);
        chatBroadcaster.broadcast(response.conversationId(), event.conversationType(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageDeleted(MessageDeletedEvent event) {
        if (event == null || event.messageId() == null || event.conversationId() == null) {
            return;
        }

        DeleteMessageResponse payload = new DeleteMessageResponse(event.messageId(), event.conversationId());
        WsEnvelope<DeleteMessageResponse> envelope = WsEnvelope.of("MESSAGE_DELETED", payload);
        chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessagePinned(MessagePinnedEvent event) {
        if (event == null || event.conversationId() == null) {
            return;
        }

        if (event.pinnedMessage() != null) {
            WsEnvelope<ChatMessageResponse> pinEnvelope = WsEnvelope.of("MESSAGE_PINNED", event.pinnedMessage());
            chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), pinEnvelope);
        }

        if (event.systemMessage() != null) {
            WsEnvelope<ChatMessageResponse> sysEnvelope = WsEnvelope.of("NEW_MESSAGE", event.systemMessage());
            chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), sysEnvelope);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageUnpinned(MessageUnpinnedEvent event) {
        if (event == null || event.messageId() == null || event.conversationId() == null) {
            return;
        }

        UnpinMessageResponse payload = new UnpinMessageResponse(event.messageId(), event.conversationId());
        WsEnvelope<UnpinMessageResponse> envelope = WsEnvelope.of("MESSAGE_UNPINNED", payload);
        chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageReactionChanged(MessageReactionChangedEvent event) {
        if (event == null || event.messageId() == null || event.conversationId() == null) {
            return;
        }

        MessageReactionEventDto payload = new MessageReactionEventDto(
                event.messageId(),
                event.conversationId(),
                event.reactions()
        );
        WsEnvelope<MessageReactionEventDto> envelope = WsEnvelope.of("MESSAGE_REACTION_CHANGED", payload);
        chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), envelope);
    }
}
