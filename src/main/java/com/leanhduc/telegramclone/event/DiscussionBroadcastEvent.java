package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;

import java.util.UUID;

public record DiscussionBroadcastEvent(
        ChatMessageResponse groupRootResponse,
        CommentCountUpdateDto commentCountUpdate,
        UUID channelConvId
) {}
