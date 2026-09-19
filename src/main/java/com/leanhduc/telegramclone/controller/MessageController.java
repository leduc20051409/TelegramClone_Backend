package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.DiscussionThreadResponse;
import com.leanhduc.telegramclone.dto.message.EditMessageRequest;
import com.leanhduc.telegramclone.service.message.IMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.leanhduc.telegramclone.dto.message.ForwardMessageRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final IMessageService messageService;

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
            @Valid @RequestBody EditMessageRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ChatMessageResponse updatedMessage = messageService.editMessage(currentUserId, messageId, request);
        return ResponseEntity.ok(updatedMessage);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageId,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        messageService.deleteMessage(currentUserId, messageId);
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

    @PostMapping("/{messageId}/forward")
    public ResponseEntity<List<ChatMessageResponse>> forwardMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody ForwardMessageRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        List<ChatMessageResponse> responses = messageService.forwardMessage(currentUserId, messageId, request);
        return ResponseEntity.ok(responses);
    }
}
