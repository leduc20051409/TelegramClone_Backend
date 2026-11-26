package com.leanhduc.telegramclone.event;

import java.util.List;
import java.util.UUID;

/**
 * Domain event published inside a DB transaction when orphaned {@code Media} records
 * are deleted and their corresponding Cloudinary assets should be destroyed after commit.
 */
public record MediaCloudDeleteEvent(List<MediaCloudTarget> targets) {

    public record MediaCloudTarget(UUID mediaId, String storageKey, String resourceType) {
    }
}
