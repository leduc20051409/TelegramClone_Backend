package com.leanhduc.telegramclone.dto.message;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ChannelViewsRequest(
        @NotEmpty(message = "Message IDs list must not be empty")
        List<Long> messageIds
) {
}
