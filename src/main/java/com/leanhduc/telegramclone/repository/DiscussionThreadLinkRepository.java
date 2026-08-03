package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.DiscussionThreadLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscussionThreadLinkRepository extends JpaRepository<DiscussionThreadLink, UUID> {

    Optional<DiscussionThreadLink> findByChannelPostMessageId(Long channelPostMessageId);

    List<DiscussionThreadLink> findByChannelPostMessageIdIn(Collection<Long> channelPostMessageIds);

    Optional<DiscussionThreadLink> findByGroupRootMessageId(Long groupRootMessageId);

    @Modifying
    @Query("UPDATE DiscussionThreadLink d SET d.commentCount = d.commentCount + 1 WHERE d.id = :id")
    void incrementCommentCount(@Param("id") UUID id);
}
