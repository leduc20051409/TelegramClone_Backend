package com.leanhduc.telegramclone.model;

import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    private String title;

    private String description;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @Column(unique = true)
    private String username;

    @Column(name = "linked_discussion_group_id", unique = true)
    private UUID linkedDiscussionGroupId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "slow_mode_delay_seconds")
    @Builder.Default
    private Integer slowModeDelaySeconds = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "conversation_default_permissions",
        joinColumns = @JoinColumn(name = "conversation_id")
    )
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<MemberPermission> defaultMemberPermissions = new HashSet<>();
}