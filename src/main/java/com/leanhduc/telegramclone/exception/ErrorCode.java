package com.leanhduc.telegramclone.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    CONTACT_ALREADY_EXISTS("Contact already exists", HttpStatus.CONFLICT),
    CANNOT_ADD_SELF("Cannot add yourself as a contact", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    CANNOT_CHAT_WITH_YOURSELF("Cannot create a conversation with yourself", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND("Conversation not found", HttpStatus.NOT_FOUND),
    NOT_IN_CONVERSATION("You are not a member of this conversation", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus status;
}
