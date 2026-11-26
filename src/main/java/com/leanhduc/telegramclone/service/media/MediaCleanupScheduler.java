package com.leanhduc.telegramclone.service.media;

import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.repository.MessageMediaRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Periodically cleans up abandoned {@link MediaStatus#TEMP} uploads older than 24 hours
 * (e.g., user closed tab or refreshed before sending) to prevent cloud storage leaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

    static final long STALE_TEMP_HOURS = 24L;

    private final MediaRepository mediaRepository;
    private final MessageMediaRepository messageMediaRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final IMediaService mediaService;

    /**
     * Runs every hour to destroy abandoned TEMP media (>24h) and retry PENDING_DELETE
     * media on Cloudinary before removing their DB rows.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupStaleTemporaryMedia() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(STALE_TEMP_HOURS);
        int deletedTempCount = cleanupCandidates(
                mediaRepository.findByStatusAndUploadedAtBefore(MediaStatus.TEMP, threshold),
                "stale TEMP"
        );
        int deletedPendingCount = cleanupCandidates(
                mediaRepository.findByStatus(MediaStatus.PENDING_DELETE),
                "PENDING_DELETE"
        );

        if (deletedTempCount > 0 || deletedPendingCount > 0) {
            log.info("Cleaned up {} stale TEMP (>{}h) and {} PENDING_DELETE media files",
                    deletedTempCount, STALE_TEMP_HOURS, deletedPendingCount);
        }
    }

    private int cleanupCandidates(List<Media> candidates, String label) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int deletedCount = 0;
        for (Media media : candidates) {
            if (isStillReferenced(media)) {
                continue;
            }
            boolean destroyed = mediaService.destroyOnCloudinary(media.getStorageKey(), media.getResourceType());
            if (destroyed) {
                mediaRepository.delete(media);
                deletedCount++;
            } else {
                log.warn("Deferring DB deletion for {} media {} because Cloudinary destroy failed", label, media.getId());
            }
        }
        return deletedCount;
    }

    private boolean isStillReferenced(Media media) {
        return messageMediaRepository.existsByMediaId(media.getId())
                || userRepository.existsByAvatarMediaId(media.getId())
                || conversationRepository.existsByAvatarMediaId(media.getId());
    }
}
