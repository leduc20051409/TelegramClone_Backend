package com.leanhduc.telegramclone.dto.websocket;

import com.leanhduc.telegramclone.model.enums.WsEventType;

import java.time.Instant;

public record WsEnvelope<T>(
        String event,
        long timestamp,
        T data
) {

    public static <T> WsEnvelope<T> of(WsEventType event, T data) {
        return of(event.name(), data);
    }

    public static <T> WsEnvelope<T> of(String event, T data) {
        return new WsEnvelope<>(event, Instant.now().toEpochMilli(), data);
    }
}
