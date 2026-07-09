package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdAndDeletedFalseOrderByIdDesc(UUID conversationId, Pageable pageable);

    /**
     * Keyset Pagination: Lấy các tin nhắn CŨ HƠN một tin nhắn cụ thể (dùng khi cuộn lên xem lịch sử).
     * Truy vấn này dùng index idx_messages_conv_not_deleted sẽ chạy siêu nhanh.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.deleted = false AND m.id < :lastMessageId " +
            "ORDER BY m.id DESC")
    List<Message> findMessagesBeforeId(
            @Param("conversationId") UUID conversationId,
            @Param("lastMessageId") Long lastMessageId,
            Pageable pageable
    );

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.deleted = false AND m.sender.id <> :userId " +
            "AND (:lastReadMessageId IS NULL OR m.id > :lastReadMessageId)")
    long countUnreadMessages(
            @Param("conversationId") UUID conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );

    List<Message> findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseAndCreatedAtBetweenOrderByIdDesc(
            UUID conversationId,
            String query,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    );

    List<Message> findByConversationIdAndDeletedFalseAndBodyContainingIgnoreCaseOrderByIdDesc(
            UUID conversationId,
            String query,
            Pageable pageable
    );

    List<Message> findByConversationIdAndDeletedFalseAndCreatedAtBetweenOrderByIdDesc(
            UUID conversationId,
            Instant startDate,
            Instant endDate,
            Pageable pageable
    );
}