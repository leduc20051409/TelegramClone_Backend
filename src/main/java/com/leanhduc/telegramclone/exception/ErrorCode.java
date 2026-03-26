package com.leanhduc.telegramclone.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    INVALID_CREDENTIALS("Invalid email or password", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),
    CONTACT_ALREADY_EXISTS("Contact already exists", HttpStatus.CONFLICT),
    CANNOT_ADD_SELF("Cannot add yourself as a contact", HttpStatus.BAD_REQUEST),
    CANNOT_CHAT_WITH_YOURSELF("Cannot create a conversation with yourself", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND("Conversation not found", HttpStatus.NOT_FOUND),
    NOT_IN_CONVERSATION("You are not a member of this conversation", HttpStatus.FORBIDDEN),
    UNSUPPORTED_FILE_TYPE("Unsupported file type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_SIZE_EXCEEDED("File size exceeds the limit", HttpStatus.PAYLOAD_TOO_LARGE);

    private final String message;
    private final HttpStatus status;
}
