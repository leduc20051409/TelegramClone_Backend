package com.leanhduc.telegramclone.shared.domain.valueobject;

import lombok.Value;

@Value
public class MessageId {
    Long value;

    public MessageId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("MessageId must be positive");
        }
        this.value = value;
    }
}
