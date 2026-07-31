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
    PASSWORDS_DO_NOT_MATCH("Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_RESET_REQUEST("Invalid or expired request", HttpStatus.BAD_REQUEST),
    INVALID_INPUT("Invalid input parameter", HttpStatus.BAD_REQUEST),

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
    USERNAME_ALREADY_EXISTS("Username is already taken", HttpStatus.CONFLICT),
    INVALID_USERNAME_FORMAT("Username must be between 3 and 32 characters, using lowercase letters, numbers, and underscores", HttpStatus.BAD_REQUEST),
    USERNAME_REQUIRED_FOR_PUBLIC("Username is required for public groups or channels", HttpStatus.BAD_REQUEST),

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
    ADMIN_REQUIRED("Only conversation owners or admins can perform this action", HttpStatus.FORBIDDEN),

    // ================= DISCUSSION GROUP =================
    CANNOT_LINK_SAME_CONVERSATION("Cannot link a conversation to itself", HttpStatus.BAD_REQUEST),
    INVALID_CONVERSATION_TYPES("Source must be a CHANNEL and target must be a GROUP", HttpStatus.BAD_REQUEST),
    GROUP_ALREADY_LINKED("This Group is already linked to another Channel", HttpStatus.CONFLICT),
    CHANNEL_ALREADY_HAS_DISCUSSION("This Channel is already linked to a Group", HttpStatus.CONFLICT),
    NOT_ADMIN_OF_BOTH_CONVERSATIONS("You must be an owner or admin of both the Channel and the Group", HttpStatus.FORBIDDEN),
    DISCUSSION_NOT_LINKED("Channel does not have a linked discussion group", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
