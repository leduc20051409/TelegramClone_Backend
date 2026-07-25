package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, UUID> {
    List<Media> findByIdInAndOwnerId(List<UUID> ids, UUID ownerId);

    Optional<Media> findByStorageKey(String storageKey);
}
