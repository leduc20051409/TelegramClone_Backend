package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.RefreshToken;
import com.leanhduc.telegramclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    List<RefreshToken> findByUserAndRevokedFalse(User user);

    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.user.email = :email AND r.revoked = false AND r.expiresAt > :now")
    Long countActiveSessionsByEmail(String email, Instant now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.email = :email AND r.revoked = false")
    void revokeAllByUserEmail(String email);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revoked = true")
    void deleteExpiredOrRevoked(Instant now);
}