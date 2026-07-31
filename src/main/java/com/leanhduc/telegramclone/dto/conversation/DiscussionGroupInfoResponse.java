package com.leanhduc.telegramclone.dto.conversation;

import java.util.UUID;

public record DiscussionGroupInfoResponse(
    UUID channelId,
    UUID groupId,
    String groupTitle,
    String groupAvatarUrl,
    int memberCount
) {}
