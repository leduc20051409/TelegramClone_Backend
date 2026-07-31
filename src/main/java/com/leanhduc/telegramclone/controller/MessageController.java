package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.DiscussionThreadResponse;
import com.leanhduc.telegramclone.dto.message.EditMessageRequest;
import com.leanhduc.telegramclone.dto.websocket.DeleteMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.service.conversation.IConversationService;
import com.leanhduc.telegramclone.service.message.IMessageService;
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
public class MessageController {
    private final IMessageService messageService;
    private final IConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        List<ChatMessageResponse> messages = messageService.getMessageHistory(conversationId, currentUserId, cursor, size);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{channelPostId}/discussion-thread")
    public ResponseEntity<DiscussionThreadResponse> getDiscussionThread(
            @PathVariable Long channelPostId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        DiscussionThreadResponse response = messageService.getDiscussionThread(currentUserId, channelPostId, cursor, size);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @PathVariable Long messageId,
            @RequestBody EditMessageRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ChatMessageResponse updatedMessage = messageService.editMessage(currentUserId, messageId, request);

        // Broadcast to WebSocket members
        WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("MESSAGE_EDITED", updatedMessage);
        List<UUID> memberIds = conversationService.getConversationMemberIds(updatedMessage.conversationId());
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    "/queue/chat",
                    envelope
            );
        }

        return ResponseEntity.ok(updatedMessage);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        UUID conversationId = messageService.deleteMessage(currentUserId, messageId);

        // Broadcast to WebSocket members
        DeleteMessageResponse deletePayload = new DeleteMessageResponse(messageId, conversationId);
        WsEnvelope<DeleteMessageResponse> envelope = WsEnvelope.of("MESSAGE_DELETED", deletePayload);
        List<UUID> memberIds = conversationService.getConversationMemberIds(conversationId);
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    "/queue/chat",
                    envelope
            );
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/search")
    public ResponseEntity<List<ChatMessageResponse>> searchMessages(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String date,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        List<ChatMessageResponse> messages = messageService.searchMessages(conversationId, currentUserId, query, date);
        return ResponseEntity.ok(messages);
    }
}
