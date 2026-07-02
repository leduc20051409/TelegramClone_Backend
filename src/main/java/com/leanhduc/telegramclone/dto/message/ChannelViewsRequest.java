package com.leanhduc.telegramclone.dto.message;

import java.util.List;

public record ChannelViewsRequest(
        List<Long> messageIds
) {
}
