package com.leanhduc.telegramclone.listener;

import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.dto.websocket.MemberEventResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.event.ConversationMemberChangedEvent;
import com.leanhduc.telegramclone.event.ConversationUpdatedEvent;
import com.leanhduc.telegramclone.event.InviteLinkUpdatedEvent;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.service.broadcast.ChatBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationBroadcastListener {

    private final ChatBroadcaster chatBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConversationUpdated(ConversationUpdatedEvent event) {
        if (event == null || event.conversationId() == null || event.conversation() == null) {
            return;
        }

        WsEnvelope<ConversationResponse> envelope = WsEnvelope.of("CONVERSATION_UPDATED", event.conversation());
        chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), envelope);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConversationMemberChanged(ConversationMemberChangedEvent event) {
        if (event == null || event.conversationId() == null) {
            return;
        }

        if (event.systemMessage() != null) {
            WsEnvelope<ChatMessageResponse> msgEnvelope = WsEnvelope.of("NEW_MESSAGE", event.systemMessage());
            broadcastWithOptionalRecipient(
                    event.conversationId(),
                    event.conversationType(),
                    event.additionalRecipientId(),
                    msgEnvelope
            );
        }

        if (event.eventType() != null && event.memberData() != null) {
            WsEnvelope<MemberEventResponse> eventEnvelope = WsEnvelope.of(event.eventType(), event.memberData());
            broadcastWithOptionalRecipient(
                    event.conversationId(),
                    event.conversationType(),
                    event.additionalRecipientId(),
                    eventEnvelope
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInviteLinkUpdated(InviteLinkUpdatedEvent event) {
        if (event == null || event.conversationId() == null || event.inviteLink() == null) {
            return;
        }

        WsEnvelope<InviteLinkResponse> envelope = WsEnvelope.of("INVITE_LINK_UPDATED", event.inviteLink());
        chatBroadcaster.broadcast(event.conversationId(), event.conversationType(), envelope);
    }

    private void broadcastWithOptionalRecipient(
            UUID conversationId,
            ConversationType conversationType,
            UUID additionalRecipientId,
            WsEnvelope<?> envelope
    ) {
        if (additionalRecipientId != null) {
            chatBroadcaster.broadcastIncluding(conversationId, conversationType, additionalRecipientId, envelope);
        } else {
            chatBroadcaster.broadcast(conversationId, conversationType, envelope);
        }
    }
}
