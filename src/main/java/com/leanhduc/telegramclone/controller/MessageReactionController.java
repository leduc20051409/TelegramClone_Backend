package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.message.MessageReactionDto;
import com.leanhduc.telegramclone.dto.message.ToggleReactionRequest;
import com.leanhduc.telegramclone.dto.message.ToggleReactionResult;
import com.leanhduc.telegramclone.dto.websocket.MessageReactionEventDto;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.service.conversation.IConversationService;
import com.leanhduc.telegramclone.service.message.IMessageReactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageReactionController {

    private final IMessageReactionService reactionService;
    private final IConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<List<MessageReactionDto>> toggleReaction(
            @PathVariable Long messageId,
            @RequestBody ToggleReactionRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ToggleReactionResult result = reactionService.toggleReaction(messageId, currentUserId, request.reaction());

        // Broadcast to WebSocket members
        MessageReactionEventDto eventPayload = new MessageReactionEventDto(
                messageId,
                result.conversationId(),
                result.reactions()
        );
        WsEnvelope<MessageReactionEventDto> envelope = WsEnvelope.of("MESSAGE_REACTION_CHANGED", eventPayload);
        
        List<UUID> memberIds = conversationService.getConversationMemberIds(result.conversationId());
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    "/queue/chat",
                    envelope
            );
        }

        return ResponseEntity.ok(result.reactions());
    }
}
