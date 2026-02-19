package com.leanhduc.telegramclone.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table (name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contact {

    @EmbeddedId
    private ContactId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId ("ownerId")
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contactId")
    @JoinColumn(name = "contact_id")
    private User contact;

    @Column (name = "is_muted", nullable = false)
    private boolean muted = false;

    @Column (name = "is_blocked", nullable = false)
    private boolean blocked = false;

    @Column (name = "alias", columnDefinition = "TEXT")
    private String alias;

    @CreationTimestamp
    @Column (name = "created_at", updatable = false)
    private Instant createdAt;
}
