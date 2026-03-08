package com.leanhduc.telegramclone.dto.websocket;

import java.time.Instant;


public record WsEnvelope<T>(
        String event,
        long timestamp,
        T data
) {

    public static <T> WsEnvelope<T> of(String event, T data) {
        return new WsEnvelope<>(event, Instant.now().toEpochMilli(), data);
    }
}