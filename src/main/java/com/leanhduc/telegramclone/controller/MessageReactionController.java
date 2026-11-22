package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.message.MessageReactionDto;
import com.leanhduc.telegramclone.dto.message.ToggleReactionRequest;
import com.leanhduc.telegramclone.dto.message.ToggleReactionResult;
import com.leanhduc.telegramclone.service.message.IMessageReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageReactionController {

    private final IMessageReactionService reactionService;

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<List<MessageReactionDto>> toggleReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody ToggleReactionRequest request,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        ToggleReactionResult result = reactionService.toggleReaction(messageId, currentUserId, request.reaction());
        return ResponseEntity.ok(result.reactions());
    }
}
