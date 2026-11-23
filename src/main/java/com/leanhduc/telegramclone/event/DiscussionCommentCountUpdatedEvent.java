package com.leanhduc.telegramclone.event;

import com.leanhduc.telegramclone.dto.message.CommentCountUpdateDto;

import java.util.UUID;

public record DiscussionCommentCountUpdatedEvent(
        UUID channelConvId,
        CommentCountUpdateDto commentCountUpdate
) {}
