package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(UUID conversationId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);
}