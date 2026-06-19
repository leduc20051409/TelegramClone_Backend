package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.AddMemberRequest;
import com.leanhduc.telegramclone.dto.conversation.UpdateConversationRequest;
import com.leanhduc.telegramclone.dto.conversation.UpdateRoleRequest;
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

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<Void> leaveConversation(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        conversationService.leaveConversation(userId, conversationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<ConversationResponse> addMember(
            @PathVariable UUID conversationId,
            @RequestBody AddMemberRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.addMember(requesterId, conversationId, request.getUserId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> updateConversation(
            @PathVariable UUID conversationId,
            @RequestBody UpdateConversationRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.updateConversation(requesterId, conversationId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{conversationId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        conversationService.removeMember(requesterId, conversationId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{conversationId}/members/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            @RequestBody UpdateRoleRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        conversationService.updateMemberRole(requesterId, conversationId, userId, request.getRole());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{conversationId}/members/me/mute")
    public ResponseEntity<Void> updateMemberMute(
            @PathVariable UUID conversationId,
            @RequestParam boolean isMuted,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        conversationService.updateMemberMute(requesterId, conversationId, isMuted);
        return ResponseEntity.ok().build();
    }
}