package com.leanhduc.telegramclone.controller;


import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.service.message.IMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping ("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final IMessageService messageService;

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) Long cursor,
            @RequestParam (defaultValue = "50") int size,
            Principal principal
    ) {
        UUID currentUserId = UUID.fromString(principal.getName());
        List<ChatMessageResponse> messages = messageService.getMessageHistory(conversationId, currentUserId, cursor, size);
        return ResponseEntity.ok(messages);
    }

}
