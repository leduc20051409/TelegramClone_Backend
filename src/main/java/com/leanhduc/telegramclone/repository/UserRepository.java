package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND u.id != :currentUserId")
    Page<User> searchUsers(@Param("query") String query, @Param("currentUserId") UUID currentUserId, Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.lastSeen = :time WHERE u.id = :id AND (u.lastSeen IS NULL OR u.lastSeen < :threshold)")
    void updateLastSeen(@Param("id") UUID id, @Param("time") Instant time, @Param("threshold") Instant threshold);
}
