package com.leanhduc.telegramclone.service.message.discussion;

import com.leanhduc.telegramclone.dto.message.DiscussionMediaContext;
import com.leanhduc.telegramclone.model.Message;

public interface IDiscussionService {

    Integer handleChannelPost(Message channelMessage, DiscussionMediaContext mediaContext);

    void handleCommentCreated(Message comment);
}
