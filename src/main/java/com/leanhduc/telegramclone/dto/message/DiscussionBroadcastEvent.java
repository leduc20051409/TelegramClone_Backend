package com.leanhduc.telegramclone.dto.message;

import java.util.List;
import java.util.UUID;

public record DiscussionBroadcastEvent(
        ChatMessageResponse groupRootResponse,
        List<UUID> groupMemberIds,
        CommentCountUpdateDto commentCountUpdate,
        UUID channelConvId,
        List<UUID> commentGroupMemberIds
) {}
