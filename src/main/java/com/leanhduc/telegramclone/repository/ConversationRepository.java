package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c " +
            "JOIN ConversationMember cm1 ON c.id = cm1.conversation.id " +
            "JOIN ConversationMember cm2 ON c.id = cm2.conversation.id " +
            "WHERE c.type = 'PRIVATE' " +
            "AND cm1.user.id = :user1Id " +
            "AND cm2.user.id = :user2Id")
    Optional<Conversation> findPrivateConversationByUsers(
            @Param("user1Id") UUID user1Id,
            @Param("user2Id") UUID user2Id
    );
}