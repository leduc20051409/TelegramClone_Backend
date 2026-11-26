package com.leanhduc.telegramclone.listener.media;

import com.leanhduc.telegramclone.event.MediaCloudDeleteEvent;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.service.media.IMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Destroys orphaned media assets on Cloudinary asynchronously after the database
 * transaction commits so external HTTP I/O never holds DB connections or runs on rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCloudCleanupListener {

    private final IMediaService mediaService;
    private final MediaRepository mediaRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaCloudDelete(MediaCloudDeleteEvent event) {
        if (event == null || event.targets() == null || event.targets().isEmpty()) {
            return;
        }
        for (MediaCloudDeleteEvent.MediaCloudTarget target : event.targets()) {
            boolean deleted = mediaService.destroyOnCloudinary(target.storageKey(), target.resourceType());
            if (deleted) {
                if (target.mediaId() != null) {
                    mediaRepository.findById(target.mediaId()).ifPresent(mediaRepository::delete);
                }
                log.info("Destroyed orphaned Cloudinary asset: mediaId={}, storageKey={}, resourceType={}",
                        target.mediaId(), target.storageKey(), target.resourceType());
            } else {
                log.warn("Could not destroy orphaned Cloudinary asset; leaving mediaId={} as PENDING_DELETE for scheduler retry (storageKey={}, resourceType={})",
                        target.mediaId(), target.storageKey(), target.resourceType());
            }
        }
    }
}
