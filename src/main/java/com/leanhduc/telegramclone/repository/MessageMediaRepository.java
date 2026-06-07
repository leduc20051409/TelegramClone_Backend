package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.MessageMedia;
import com.leanhduc.telegramclone.model.MessageMediaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageMediaRepository extends JpaRepository<MessageMedia, MessageMediaId> {

    @Query("SELECT mm FROM MessageMedia mm " +
            "JOIN FETCH mm.media " +
            "WHERE mm.message.id IN :messageIds " +
            "ORDER BY mm.message.id, mm.ordinal")
    List<MessageMedia> findByMessageIdInWithMedia(@Param("messageIds") List<Long> messageIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM MessageMedia mm WHERE mm.message.id = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);
}
