package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.MessagePostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MessagePostViewRepository extends JpaRepository<MessagePostView, Long> {

    @Modifying
    @Query(value = "INSERT INTO message_post_views (message_id, view_count, updated_at) " +
                   "VALUES (:messageId, 1, NOW()) " +
                   "ON CONFLICT (message_id) " +
                   "DO UPDATE SET view_count = message_post_views.view_count + 1, updated_at = NOW()",
           nativeQuery = true)
    void incrementViewCount(@Param("messageId") Long messageId);
}
