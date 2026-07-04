package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.MessageReaction;
import com.leanhduc.telegramclone.model.MessageReactionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReactionId> {
    List<MessageReaction> findByIdMessageIdAndIdUserId(Long messageId, UUID userId);
    List<MessageReaction> findByIdMessageId(Long messageId);
}
