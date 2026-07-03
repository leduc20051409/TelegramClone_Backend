package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.PinnedMessage;
import com.leanhduc.telegramclone.model.PinnedMessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, PinnedMessageId> {

    @Query("SELECT pm FROM PinnedMessage pm WHERE pm.id.conversationId = :conversationId ORDER BY pm.pinnedAt DESC")
    List<PinnedMessage> findAllByConversationIdOrderByPinnedAtDesc(@Param("conversationId") UUID conversationId);
}
