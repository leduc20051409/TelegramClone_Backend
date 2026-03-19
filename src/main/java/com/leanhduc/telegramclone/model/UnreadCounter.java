package com.leanhduc.telegramclone.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "unread_counters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCounter {

    @EmbeddedId
    private UnreadCounterId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}