package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.service.conversation.IConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final IConversationService conversationService;

    @PostMapping("/private/{targetUserId}")
    public ResponseEntity<ConversationResponse> getOrCreatePrivateChat(
            @PathVariable UUID targetUserId,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.getOrCreatePrivateConversation(currentUserId, targetUserId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(
            @RequestBody CreateGroupRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.createGroupConversation(currentUserId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public ResponseEntity<List<ConversationResponse>> getAllConversations(Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        List<ConversationResponse> conversations = conversationService.getAllConversationsByUser(userId);
        return ResponseEntity.ok(conversations);
    }
}