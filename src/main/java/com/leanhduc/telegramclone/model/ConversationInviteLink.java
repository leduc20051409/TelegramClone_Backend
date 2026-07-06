package com.leanhduc.telegramclone.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversation_invite_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationInviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "invite_code", unique = true, nullable = false)
    private String inviteCode;

    private String name;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "member_limit")
    @Builder.Default
    private int memberLimit = 0;

    @Column(name = "current_uses")
    @Builder.Default
    private int currentUses = 0;

    @Column(name = "is_revoked")
    @Builder.Default
    private boolean isRevoked = false;

    @Column(name = "is_primary")
    @Builder.Default
    private boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
