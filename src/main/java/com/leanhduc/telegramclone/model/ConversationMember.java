package com.leanhduc.telegramclone.model;

import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ConversationRole role = ConversationRole.MEMBER;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "is_muted", nullable = false)
    @Builder.Default
    private boolean isMuted = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "conversation_member_permissions",
        joinColumns = {
            @JoinColumn(name = "conversation_id", referencedColumnName = "conversation_id"),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id")
        }
    )
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<MemberPermission> memberPermissions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "conversation_admin_permissions",
        joinColumns = {
            @JoinColumn(name = "conversation_id", referencedColumnName = "conversation_id"),
            @JoinColumn(name = "user_id", referencedColumnName = "user_id")
        }
    )
    @Column(name = "permission")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<AdminPermission> adminPermissions = new HashSet<>();
}