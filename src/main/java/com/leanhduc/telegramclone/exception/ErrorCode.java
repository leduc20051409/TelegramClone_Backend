package com.leanhduc.telegramclone.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    CONTACT_ALREADY_EXISTS("Người này đã có trong danh bạ", HttpStatus.CONFLICT),
    CANNOT_ADD_SELF("Không thể add chính mình", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("Không tìm thấy người dùng", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus status;
}
