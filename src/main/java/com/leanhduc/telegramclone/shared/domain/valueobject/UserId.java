package com.leanhduc.telegramclone.shared.domain.valueobject;

import lombok.*;

import java.util.UUID;

@Value
public class UserId {
    UUID value;

    public UserId(String value) {
        this.value = UUID.fromString(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public UserId(UUID value) {
        if (value == null) throw new IllegalArgumentException("UserId cannot be null");
        this.value = value;
    }

}
