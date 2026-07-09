package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(UUID conversationId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);

    @Query("SELECT DISTINCT m2.user.id FROM ConversationMember m1 " +
           "JOIN ConversationMember m2 ON m1.conversation.id = m2.conversation.id " +
           "WHERE m1.user.id = :userId AND m2.user.id != :userId AND m1.leftAt IS NULL AND m2.leftAt IS NULL")
    List<UUID> findRelatedUserIds(@Param("userId") UUID userId);
}