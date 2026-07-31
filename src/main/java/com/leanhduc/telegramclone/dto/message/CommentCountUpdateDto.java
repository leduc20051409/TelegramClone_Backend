package com.leanhduc.telegramclone.dto.message;

import java.util.UUID;

public record CommentCountUpdateDto(
    Long channelPostId,
    UUID channelId,
    Long groupRootMessageId,
    UUID groupId,
    int newCommentCount
) {}
