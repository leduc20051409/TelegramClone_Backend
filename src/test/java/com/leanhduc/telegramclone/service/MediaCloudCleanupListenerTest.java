package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.event.MediaCloudDeleteEvent;
import com.leanhduc.telegramclone.listener.media.MediaCloudCleanupListener;
import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.service.media.IMediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaCloudCleanupListenerTest {

    @Mock
    private IMediaService mediaService;
    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private MediaCloudCleanupListener listener;

    @Test
    @DisplayName("Should delete PENDING_DELETE DB row when Cloudinary destroy succeeds")
    void onMediaCloudDelete_whenCloudinarySucceeds_deletesDbRow() {
        UUID mediaId = UUID.randomUUID();
        Media pending = Media.builder()
                .id(mediaId)
                .storageKey("folder/clip")
                .resourceType("video")
                .status(MediaStatus.PENDING_DELETE)
                .build();

        when(mediaService.destroyOnCloudinary("folder/clip", "video")).thenReturn(true);
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(pending));

        listener.onMediaCloudDelete(new MediaCloudDeleteEvent(
                List.of(new MediaCloudDeleteEvent.MediaCloudTarget(mediaId, "folder/clip", "video"))
        ));

        verify(mediaRepository).delete(pending);
    }

    @Test
    @DisplayName("Should keep PENDING_DELETE DB row when Cloudinary destroy fails")
    void onMediaCloudDelete_whenCloudinaryFails_keepsDbRowForRetry() {
        UUID mediaId = UUID.randomUUID();

        when(mediaService.destroyOnCloudinary("folder/clip", "video")).thenReturn(false);

        listener.onMediaCloudDelete(new MediaCloudDeleteEvent(
                List.of(new MediaCloudDeleteEvent.MediaCloudTarget(mediaId, "folder/clip", "video"))
        ));

        verify(mediaRepository, never()).findById(any());
        verify(mediaRepository, never()).delete(any());
    }
}
