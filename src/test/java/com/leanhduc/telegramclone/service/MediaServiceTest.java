package com.leanhduc.telegramclone.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import com.leanhduc.telegramclone.service.media.MediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private IPermissionService permissionService;

    @InjectMocks
    private MediaService mediaService;

    @Test
    @DisplayName("Should upload video (.mov) with resource_type=video and unique UUID public_id")
    @SuppressWarnings("unchecked")
    void uploadFile_videoMov_shouldSucceedWithUniquePublicId() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile videoFile = new MockMultipartFile(
                "file", "clip.mov", "video/quicktime", new byte[1024]
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/video/upload/clip.mov",
                "public_id", "telegram-clone/" + userId + "/uuid_clip",
                "width", 1920,
                "height", 1080,
                "duration", 12.5
        ));
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
            Media m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        UploadResponseDto dto = mediaService.uploadFile(userId, videoFile);

        assertNotNull(dto.mediaId());
        assertEquals("video/quicktime", dto.fileType());

        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals("video", options.get("resource_type"));
        String publicId = (String) options.get("public_id");
        assertNotNull(publicId);
        assertTrue(publicId.endsWith("_clip"));
        assertNotEquals("clip", publicId);
    }

    @Test
    @DisplayName("Should upload video with asFile=true using resource_type=raw and preserving extension")
    @SuppressWarnings("unchecked")
    void uploadFile_videoAsFile_shouldUseRawResourceTypeAndKeepExtension() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile videoFile = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", new byte[1024]
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/raw/upload/uuid_clip.mp4",
                "public_id", "telegram-clone/" + userId + "/uuid_clip.mp4"
        ));
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
            Media m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        UploadResponseDto dto = mediaService.uploadFile(userId, videoFile, true);

        assertNotNull(dto.mediaId());
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals("raw", options.get("resource_type"));
        String publicId = (String) options.get("public_id");
        assertNotNull(publicId);
        assertTrue(publicId.endsWith("_clip.mp4"));
    }

    @Test
    @DisplayName("Should reject image larger than 10 MB with FILE_SIZE_EXCEEDED")
    void uploadFile_imageOver10Mb_shouldThrow() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile largeImage = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[(10 * 1024 * 1024) + 1]
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> mediaService.uploadFile(userId, largeImage));
        assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should allow 15 MB video (under 50 MB limit)")
    void uploadFile_video15Mb_shouldPassSizeCheck() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile video = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[15 * 1024 * 1024]
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/video/upload/demo.mp4",
                "public_id", "telegram-clone/" + userId + "/uuid_demo"
        ));
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
            Media m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        UploadResponseDto dto = mediaService.uploadFile(userId, video);
        assertNotNull(dto.mediaId());
        verify(uploader).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("destroyOnCloudinary should return true for ok/not found and false on IOException")
    void destroyOnCloudinary_handlesOkNotFoundAndErrors() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq("key1"), anyMap())).thenReturn(Map.of("result", "ok"));
        when(uploader.destroy(eq("key2"), anyMap())).thenReturn(Map.of("result", "not found"));
        when(uploader.destroy(eq("key3"), anyMap())).thenThrow(new IOException("network error"));

        assertTrue(mediaService.destroyOnCloudinary("key1", "video"));
        assertTrue(mediaService.destroyOnCloudinary("key2", "video"));
        assertFalse(mediaService.destroyOnCloudinary("key3", "video"));
    }
}
