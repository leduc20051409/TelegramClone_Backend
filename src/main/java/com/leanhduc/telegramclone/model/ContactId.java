package com.leanhduc.telegramclone.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode (onlyExplicitlyIncluded = true)
@Embeddable
public class ContactId implements Serializable {
    @Column (name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column (name = "contact_id", nullable = false)
    private UUID contactId;
}
