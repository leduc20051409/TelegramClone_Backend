package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

        List<Message> findByConversationIdAndDeletedFalseOrderByIdDesc(UUID conversationId, Pageable pageable);

        Optional<Message> findByIdAndConversationId(Long id, UUID conversationId);

        @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
                        "AND m.deleted = false AND m.id < :lastMessageId " +
                        "ORDER BY m.id DESC")
        List<Message> findMessagesBeforeId(
                        @Param("conversationId") UUID conversationId,
                        @Param("lastMessageId") Long lastMessageId,
                        Pageable pageable);

        @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
                        "AND m.deleted = false AND m.sender.id <> :userId " +
                        "AND (:lastReadMessageId IS NULL OR m.id > :lastReadMessageId)")
        long countUnreadMessages(
                        @Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId,
                        @Param("lastReadMessageId") Long lastReadMessageId);

        List<Message> findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseAndCreatedAtBetweenOrderByIdDesc(
                        UUID conversationId,
                        String query,
                        Instant startDate,
                        Instant endDate,
                        Pageable pageable);

        List<Message> findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseOrderByIdDesc(
                        UUID conversationId,
                        String query,
                        Pageable pageable);

        List<Message> findByConversationIdAndDeletedFalseAndCreatedAtBetweenOrderByIdDesc(
                        UUID conversationId,
                        Instant startDate,
                        Instant endDate,
                        Pageable pageable);

        @Query(value = "WITH RECURSIVE thread_tree AS (" +
                       "  SELECT id FROM messages WHERE id = :groupRootMessageId " +
                       "  UNION ALL " +
                       "  SELECT m.id FROM messages m " +
                       "  INNER JOIN thread_tree tt ON m.reply_to_message_id = tt.id " +
                       ") " +
                       "SELECT m.* FROM messages m " +
                       "INNER JOIN thread_tree tt ON m.id = tt.id " +
                       "WHERE tt.id <> :groupRootMessageId AND m.deleted = false " +
                       "ORDER BY m.id ASC",
                       nativeQuery = true)
        List<Message> findThreadComments(@Param("groupRootMessageId") Long groupRootMessageId, Pageable pageable);

        @Query(value = "WITH RECURSIVE thread_tree AS (" +
                       "  SELECT id FROM messages WHERE id = :groupRootMessageId " +
                       "  UNION ALL " +
                       "  SELECT m.id FROM messages m " +
                       "  INNER JOIN thread_tree tt ON m.reply_to_message_id = tt.id " +
                       ") " +
                       "SELECT m.* FROM messages m " +
                       "INNER JOIN thread_tree tt ON m.id = tt.id " +
                       "WHERE tt.id <> :groupRootMessageId AND m.id > :lastCommentId AND m.deleted = false " +
                       "ORDER BY m.id ASC",
                       nativeQuery = true)
        List<Message> findThreadCommentsAfterId(@Param("groupRootMessageId") Long groupRootMessageId,
                        @Param("lastCommentId") Long lastCommentId, Pageable pageable);
}