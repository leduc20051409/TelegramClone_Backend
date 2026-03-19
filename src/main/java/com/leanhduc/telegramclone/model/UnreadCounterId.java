package com.leanhduc.telegramclone.model;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UnreadCounterId implements Serializable {
    private UUID conversationId;
    private UUID userId;
}