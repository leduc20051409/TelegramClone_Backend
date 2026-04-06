package com.leanhduc.telegramclone.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class MessageMediaId implements Serializable {

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "media_id", columnDefinition = "uuid")
    private UUID mediaId;
}
