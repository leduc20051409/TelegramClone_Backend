package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.ConversationInviteLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationInviteLinkRepository extends JpaRepository<ConversationInviteLink, Long> {

    Optional<ConversationInviteLink> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ConversationInviteLink c WHERE c.inviteCode = :inviteCode")
    Optional<ConversationInviteLink> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    List<ConversationInviteLink> findAllByConversationIdAndIsRevokedFalse(UUID conversationId);

    @Modifying
    @Query("UPDATE ConversationInviteLink c SET c.isPrimary = false WHERE c.conversation.id = :conversationId AND c.isPrimary = true")
    void demotePrimaryLinks(@Param("conversationId") UUID conversationId);
}
