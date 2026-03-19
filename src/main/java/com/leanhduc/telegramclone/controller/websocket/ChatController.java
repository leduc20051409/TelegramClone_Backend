package com.leanhduc.telegramclone.controller.websocket;

import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.ChatReadRequest;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.service.conversation.IConversationService;
import com.leanhduc.telegramclone.service.message.IMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final IMessageService messageService;
    private final IConversationService conversationService;

    @MessageMapping ("/chat.send")
    public void sendMessage(@Payload ChatMessageRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        ChatMessageResponse savedMessage = messageService.saveMessage(senderId, request);
        WsEnvelope<ChatMessageResponse> envelope = WsEnvelope.of("NEW_MESSAGE", savedMessage);
        List<UUID> memberIds = conversationService.getConversationMemberIds(request.conversationId());
        for (UUID memberId : memberIds) {
            messagingTemplate.convertAndSendToUser(
                    memberId.toString(),
                    "/queue/chat",
                    envelope
            );
        }
    }

    @MessageMapping ("/chat.read")
    public void markAsRead(@Payload ChatReadRequest request, Principal principal) {
        UUID readerId = UUID.fromString(principal.getName());
        messageService.markMessagesAsRead(readerId, request);
        WsEnvelope<ChatReadRequest> envelope = WsEnvelope.of("MESSAGES_READ", request);
        List<UUID> memberIds = conversationService.getConversationMemberIds(request.conversationId());
        for (UUID memberId : memberIds) {
            if (!memberId.equals(readerId)) {
                messagingTemplate.convertAndSendToUser(
                        memberId.toString(),
                        "/queue/chat",
                        envelope
                );
            }
        }
    }
}