package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.conversation.CreateGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.AddMemberRequest;
import com.leanhduc.telegramclone.dto.conversation.DiscussionGroupInfoResponse;
import com.leanhduc.telegramclone.dto.conversation.LinkDiscussionGroupRequest;
import com.leanhduc.telegramclone.dto.conversation.UpdateConversationRequest;
import jakarta.validation.Valid;
import com.leanhduc.telegramclone.dto.conversation.UpdateRoleRequest;
import com.leanhduc.telegramclone.dto.conversation.ConversationResponse;
import com.leanhduc.telegramclone.service.conversation.IConversationService;
import com.leanhduc.telegramclone.service.message.IMessageService;
import com.leanhduc.telegramclone.dto.message.ChannelViewsRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.dto.websocket.UnpinMessageResponse;
import com.leanhduc.telegramclone.dto.message.PinMessageResult;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final IConversationService conversationService;
    private final IMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

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

    @PostMapping("/{conversationId}/join")
    public ResponseEntity<ConversationResponse> joinConversation(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.addMember(userId, conversationId, userId);
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

        // Broadcast the update to all members of the conversation in real-time
        WsEnvelope<ConversationResponse> envelope = WsEnvelope.of("CONVERSATION_UPDATED", response);
        ConversationType type = conversationService.getConversationType(conversationId);
        List<UUID> memberIds = conversationService.getConversationMemberIds(conversationId);

        if (type == ConversationType.CHANNEL && memberIds.size() > 1000) {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + conversationId,
                    envelope
            );
        } else {
            for (UUID memberId : memberIds) {
                messagingTemplate.convertAndSendToUser(
                        memberId.toString(),
                        "/queue/chat",
                        envelope
                );
            }
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID conversationId,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        conversationService.deleteConversation(requesterId, conversationId);
        return ResponseEntity.noContent().build();
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

    @PostMapping("/{conversationId}/views")
    public ResponseEntity<Void> incrementViews(
            @PathVariable UUID conversationId,
            @RequestBody ChannelViewsRequest request,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        messageService.incrementViews(userId, conversationId, request.messageIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{conversationId}/pin/{messageId}")
    public ResponseEntity<ChatMessageResponse> pinMessage(
            @PathVariable UUID conversationId,
            @PathVariable Long messageId,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        PinMessageResult result = messageService.pinMessage(userId, conversationId, messageId);
        ChatMessageResponse pinnedResponse = result.pinnedMessage();
        ChatMessageResponse systemResponse = result.systemMessage();

        ConversationType type = conversationService.getConversationType(conversationId);
        List<UUID> memberIds = conversationService.getConversationMemberIds(conversationId);

        // 1. Broadcast the Pinned Message update (MESSAGE_PINNED)
        WsEnvelope<ChatMessageResponse> pinEnvelope = WsEnvelope.of("MESSAGE_PINNED", pinnedResponse);
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    "/queue/chat",
                    pinEnvelope
            );
        }

        // 2. Broadcast the SYSTEM notification message (NEW_MESSAGE)
        WsEnvelope<ChatMessageResponse> sysEnvelope = WsEnvelope.of("NEW_MESSAGE", systemResponse);
        if (type == ConversationType.CHANNEL && memberIds.size() > 1000) {
            messagingTemplate.convertAndSend(
                    "/topic/channels/" + conversationId,
                    sysEnvelope
            );
        } else {
            for (UUID memberId : memberIds) {
                messagingTemplate.convertAndSendToUser(
                        memberId.toString(),
                        "/queue/chat",
                        sysEnvelope
                );
            }
        }

        return ResponseEntity.ok(pinnedResponse);
    }

    @DeleteMapping("/{conversationId}/unpin/{messageId}")
    public ResponseEntity<Void> unpinMessage(
            @PathVariable UUID conversationId,
            @PathVariable Long messageId,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        messageService.unpinMessage(userId, conversationId, messageId);

        // Broadcast to WebSocket members
        UnpinMessageResponse payload = new UnpinMessageResponse(messageId, conversationId);
        WsEnvelope<UnpinMessageResponse> envelope = WsEnvelope.of("MESSAGE_UNPINNED", payload);
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

    @GetMapping("/search")
    public ResponseEntity<List<ConversationResponse>> searchPublicConversations(@RequestParam String query) {
        return ResponseEntity.ok(conversationService.searchPublicConversations(query));
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<ConversationResponse> getPublicConversationByUsername(@PathVariable String username) {
        return ResponseEntity.ok(conversationService.getPublicConversationByUsername(username));
    }

    @PostMapping("/{channelId}/discussion/link")
    public ResponseEntity<DiscussionGroupInfoResponse> linkDiscussionGroup(
            @PathVariable UUID channelId,
            @Valid @RequestBody LinkDiscussionGroupRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        DiscussionGroupInfoResponse response = conversationService.linkDiscussionGroup(channelId, request.groupId(), requesterId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{channelId}/discussion/unlink")
    public ResponseEntity<Void> unlinkDiscussionGroup(
            @PathVariable UUID channelId,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        conversationService.unlinkDiscussionGroup(channelId, requesterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{channelId}/discussion")
    public ResponseEntity<DiscussionGroupInfoResponse> getLinkedDiscussionGroup(
            @PathVariable UUID channelId,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        DiscussionGroupInfoResponse response = conversationService.getLinkedDiscussionGroup(channelId, requesterId);
        return ResponseEntity.ok(response);
    }
}