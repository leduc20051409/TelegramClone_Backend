package com.leanhduc.telegramclone.dto.message;

import java.util.List;
import java.util.UUID;

public record DiscussionThreadResponse(
    Long channelPostId,
    UUID channelId,
    Long groupRootMessageId,
    UUID groupId,
    int commentCount,
    ChatMessageResponse rootMessage,
    List<ChatMessageResponse> comments
) {}
