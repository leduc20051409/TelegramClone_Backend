package com.leanhduc.telegramclone.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // ================= AUTH =================
    INVALID_CREDENTIALS("Invalid email or password", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED("Unauthorized", HttpStatus.UNAUTHORIZED),

    // ================= USER =================
    USER_NOT_FOUND("User not found", HttpStatus.NOT_FOUND),

    // ================= CONTACT =================
    CONTACT_ALREADY_EXISTS("Contact already exists", HttpStatus.CONFLICT),
    CANNOT_ADD_SELF("Cannot add yourself as a contact", HttpStatus.BAD_REQUEST),

    // ================= CONVERSATION =================
    CANNOT_CHAT_WITH_YOURSELF("Cannot create a conversation with yourself", HttpStatus.BAD_REQUEST),
    CONVERSATION_NOT_FOUND("Conversation not found", HttpStatus.NOT_FOUND),
    NOT_IN_CONVERSATION("You are not a member of this conversation", HttpStatus.FORBIDDEN),
    INVALID_CONVERSATION_TITLE("Conversation title must be between 1 and 100 characters", HttpStatus.BAD_REQUEST),
    INVALID_CONVERSATION_DESCRIPTION("Conversation description must not exceed 1000 characters", HttpStatus.BAD_REQUEST),

    // ================= FILE / MEDIA =================
    INVALID_FILE("Invalid file", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE("Unsupported file type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INVALID_FILE_EXTENSION("Invalid file extension", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_SIZE_EXCEEDED("File size exceeds the limit", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_UPLOAD_FAILED("Failed to upload file", HttpStatus.INTERNAL_SERVER_ERROR),
    MEDIA_NOT_ACCESSIBLE("Media not accessible", HttpStatus.FORBIDDEN),
    DUPLICATE_MEDIA("Duplicate media in message", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND("Media not found", HttpStatus.NOT_FOUND),
    DELETE_MEDIA_FAILED("Failed to delete media", HttpStatus.INTERNAL_SERVER_ERROR),
    MESSAGE_NOT_FOUND("Message not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_MESSAGE_ACTION("You can only modify or delete your own messages", HttpStatus.FORBIDDEN),
    SUBSCRIBERS_CANNOT_POST("Subscribers cannot post in a channel", HttpStatus.FORBIDDEN),

    // ================= INVITE LINK =================
    INVITE_LINK_NOT_FOUND("Invite link not found", HttpStatus.NOT_FOUND),
    INVITE_LINK_EXPIRED("Invite link has expired", HttpStatus.GONE),
    INVITE_LINK_LIMIT_REACHED("Invite link limit has been reached", HttpStatus.GONE),
    INVITE_LINK_REVOKED("Invite link has been revoked", HttpStatus.GONE),
    ALREADY_IN_CONVERSATION("User is already a member of this conversation", HttpStatus.CONFLICT),
    ADMIN_REQUIRED("Only conversation owners or admins can perform this action", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus status;
}
