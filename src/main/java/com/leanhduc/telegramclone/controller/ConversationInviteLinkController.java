package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.invite.CreateInviteLinkRequest;
import com.leanhduc.telegramclone.dto.invite.InviteLinkInfoResponse;
import com.leanhduc.telegramclone.dto.invite.InviteLinkResponse;
import com.leanhduc.telegramclone.dto.invite.UpdateInviteLinkRequest;
import com.leanhduc.telegramclone.service.invite.IConversationInviteLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ConversationInviteLinkController {

    private final IConversationInviteLinkService inviteLinkService;

    @PostMapping("/api/conversations/{id}/invite-links")
    public ResponseEntity<InviteLinkResponse> createInviteLink(
            @PathVariable UUID id,
            @RequestBody CreateInviteLinkRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        InviteLinkResponse response = inviteLinkService.createInviteLink(requesterId, id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/invite/{id}")
    public ResponseEntity<InviteLinkResponse> updateInviteLink(
            @PathVariable Long id,
            @RequestBody UpdateInviteLinkRequest request,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        InviteLinkResponse response = inviteLinkService.updateInviteLink(requesterId, id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/conversations/{id}/invite-links")
    public ResponseEntity<List<InviteLinkResponse>> getInviteLinks(
            @PathVariable UUID id,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        List<InviteLinkResponse> response = inviteLinkService.getInviteLinksForConversation(requesterId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/invite/{inviteCode}")
    public ResponseEntity<InviteLinkInfoResponse> getInviteLinkInfo(
            @PathVariable String inviteCode
    ) {
        InviteLinkInfoResponse response = inviteLinkService.getInviteLinkInfo(inviteCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/invite/{inviteCode}/join")
    public ResponseEntity<InviteLinkResponse> joinConversation(
            @PathVariable String inviteCode,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        InviteLinkResponse response = inviteLinkService.joinConversation(userId, inviteCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/invite/{id}/revoke")
    public ResponseEntity<InviteLinkResponse> revokeInviteLink(
            @PathVariable Long id,
            Principal principal
    ) {
        UUID requesterId = UUID.fromString(principal.getName());
        InviteLinkResponse response = inviteLinkService.revokeInviteLink(requesterId, id);
        return ResponseEntity.ok(response);
    }
}
