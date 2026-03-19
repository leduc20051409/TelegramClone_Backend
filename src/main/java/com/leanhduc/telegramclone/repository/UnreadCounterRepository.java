package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.UnreadCounter;
import com.leanhduc.telegramclone.model.UnreadCounterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnreadCounterRepository extends JpaRepository<UnreadCounter, UnreadCounterId> {
}