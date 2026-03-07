package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    // Lấy tất cả thành viên của một phòng chat cụ thể
    List<ConversationMember> findByConversationId(UUID conversationId);

    // Kiểm tra xem user có phải là thành viên của phòng chat không (dùng để Validate khi gửi tin nhắn)
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);
}