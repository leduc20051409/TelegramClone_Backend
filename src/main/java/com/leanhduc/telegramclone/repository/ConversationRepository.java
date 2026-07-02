package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Query("SELECT DISTINCT c FROM Conversation c " +
            "JOIN ConversationMember cm ON c.id = cm.conversation.id " +
            "WHERE cm.user.id = :userId AND cm.leftAt IS NULL")
    List<Conversation> findAllByMember(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM pinned_messages WHERE conversation_id = :conversationId", nativeQuery = true)
    void deletePinnedMessagesByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM unread_counters WHERE conversation_id = :conversationId", nativeQuery = true)
    void deleteUnreadCountersByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM message_media WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)", nativeQuery = true)
    void deleteMessageMediaByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM message_reactions WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)", nativeQuery = true)
    void deleteMessageReactionsByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM message_post_views WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)", nativeQuery = true)
    void deleteMessagePostViewsByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM conversation_members WHERE conversation_id = :conversationId", nativeQuery = true)
    void deleteConversationMembersByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query(value = "DELETE FROM messages WHERE conversation_id = :conversationId", nativeQuery = true)
    void deleteMessagesByConversationId(@Param("conversationId") UUID conversationId);
}