package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;

import java.util.List;
import java.util.UUID;

public record DiscussionBroadcastEvent(
        ChatMessageResponse groupRootResponse,
        List<UUID> groupMemberIds,
        CommentCountUpdateDto commentCountUpdate,
        UUID channelConvId,
        List<UUID> commentGroupMemberIds
) {}
