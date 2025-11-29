package com.leanhduc.telegramclone.shared.domain.valueobject;

import lombok.Value;

import java.util.UUID;

@Value
public class ConversationId {
    UUID value;

    public ConversationId(String value) {
        this.value = UUID.fromString(value);
    }

    public static ConversationId generate() {
        return new ConversationId(UUID.randomUUID());
    }

    public ConversationId(UUID value) {
        if (value == null) throw new IllegalArgumentException("ConversationId cannot be null");
        this.value = value;
    }

}
