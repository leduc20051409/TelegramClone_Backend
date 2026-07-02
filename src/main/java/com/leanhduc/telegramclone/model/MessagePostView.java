package com.leanhduc.telegramclone.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "message_post_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessagePostView {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "message_id")
    private Message message;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
