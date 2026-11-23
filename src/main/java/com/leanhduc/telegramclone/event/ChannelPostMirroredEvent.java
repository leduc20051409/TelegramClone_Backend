package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;

public record ChannelPostMirroredEvent(
        ChatMessageResponse groupRootResponse
) {}
