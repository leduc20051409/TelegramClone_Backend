package com.leanhduc.telegramclone.service;

import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.repository.MessageMediaRepository;
import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.service.media.IMediaService;
import com.leanhduc.telegramclone.service.media.MediaCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaCleanupSchedulerTest {

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MessageMediaRepository messageMediaRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private IMediaService mediaService;

    @InjectMocks
    private MediaCleanupScheduler scheduler;

    @Test
    @DisplayName("Should destroy unreferenced stale TEMP media, skip avatar-referenced, and defer on Cloudinary failure")
    void cleanupStaleTemporaryMedia_handlesAllCases() {
        Media orphanVideo = Media.builder()
                .id(UUID.randomUUID())
                .storageKey("folder/orphan_video")
                .resourceType("video")
                .status(MediaStatus.TEMP)
                .build();
        Media avatarRef = Media.builder()
                .id(UUID.randomUUID())
                .storageKey("folder/group_avatar")
                .resourceType("image")
                .status(MediaStatus.TEMP)
                .build();
        Media failedDestroy = Media.builder()
                .id(UUID.randomUUID())
                .storageKey("folder/retry_video")
                .resourceType("video")
                .status(MediaStatus.TEMP)
                .build();

        when(mediaRepository.findByStatusAndUploadedAtBefore(eq(MediaStatus.TEMP), any(OffsetDateTime.class)))
                .thenReturn(List.of(orphanVideo, avatarRef, failedDestroy));
        when(mediaRepository.findByStatus(MediaStatus.PENDING_DELETE))
                .thenReturn(List.of());

        when(messageMediaRepository.existsByMediaId(any())).thenReturn(false);
        when(userRepository.existsByAvatarMediaId(any())).thenReturn(false);
        when(conversationRepository.existsByAvatarMediaId(orphanVideo.getId())).thenReturn(false);
        when(conversationRepository.existsByAvatarMediaId(avatarRef.getId())).thenReturn(true);
        when(conversationRepository.existsByAvatarMediaId(failedDestroy.getId())).thenReturn(false);

        when(mediaService.destroyOnCloudinary("folder/orphan_video", "video")).thenReturn(true);
        when(mediaService.destroyOnCloudinary("folder/retry_video", "video")).thenReturn(false);

        scheduler.cleanupStaleTemporaryMedia();

        verify(mediaRepository).delete(orphanVideo);
        verify(mediaService, never()).destroyOnCloudinary("folder/group_avatar", "image");
        verify(mediaRepository, never()).delete(avatarRef);
        verify(mediaRepository, never()).delete(failedDestroy);
    }

    @Test
    @DisplayName("Should retry PENDING_DELETE media and delete DB row only when Cloudinary destroy succeeds")
    void cleanupStaleTemporaryMedia_retriesPendingDeleteRecords() {
        Media pendingOk = Media.builder()
                .id(UUID.randomUUID())
                .storageKey("folder/pending_ok")
                .resourceType("video")
                .status(MediaStatus.PENDING_DELETE)
                .build();
        Media pendingFail = Media.builder()
                .id(UUID.randomUUID())
                .storageKey("folder/pending_fail")
                .resourceType("raw")
                .status(MediaStatus.PENDING_DELETE)
                .build();

        when(mediaRepository.findByStatusAndUploadedAtBefore(eq(MediaStatus.TEMP), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(mediaRepository.findByStatus(MediaStatus.PENDING_DELETE))
                .thenReturn(List.of(pendingOk, pendingFail));
        when(messageMediaRepository.existsByMediaId(any())).thenReturn(false);
        when(userRepository.existsByAvatarMediaId(any())).thenReturn(false);
        when(conversationRepository.existsByAvatarMediaId(any())).thenReturn(false);
        when(mediaService.destroyOnCloudinary("folder/pending_ok", "video")).thenReturn(true);
        when(mediaService.destroyOnCloudinary("folder/pending_fail", "raw")).thenReturn(false);

        scheduler.cleanupStaleTemporaryMedia();

        verify(mediaRepository).delete(pendingOk);
        verify(mediaRepository, never()).delete(pendingFail);
    }
}
