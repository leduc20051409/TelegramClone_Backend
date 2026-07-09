package com.leanhduc.telegramclone.repository;

import com.leanhduc.telegramclone.model.Contact;
import com.leanhduc.telegramclone.model.ContactId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, ContactId> {

    @EntityGraph (attributePaths = "contact")
    Page<Contact> findByIdOwnerIdAndBlockedFalse(UUID ownerId, Pageable pageable);

    Page<Contact> findByIdOwnerIdAndBlockedTrue(UUID ownerId, Pageable pageable);
    Optional<Contact> findByIdOwnerIdAndIdContactId(UUID ownerId, UUID contactId);

    @Query ("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Contact c WHERE c.id.ownerId = :ownerId " +
            "AND c.id.contactId = :contactId " +
            "AND c.blocked = true")
    boolean existsByOwnerIdAndContactIdAndIsBlockedTrue(@Param ("ownerId") UUID ownerId,
                                                        @Param("contactId") UUID contactId);

    @Query("SELECT DISTINCT c.id.ownerId FROM Contact c WHERE c.id.contactId = :contactId AND c.blocked = false")
    List<UUID> findOwnerIdsByContactIdAndBlockedFalse(@Param("contactId") UUID contactId);
}
