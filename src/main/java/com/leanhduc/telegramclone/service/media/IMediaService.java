package com.leanhduc.telegramclone.service.media;

import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public interface IMediaService {
    UploadResponseDto uploadFile(UUID userId, MultipartFile file);

    UploadResponseDto uploadFile(UUID userId, MultipartFile file, boolean asFile);

    UploadResponseDto uploadAvatar(UUID ownerId, MultipartFile file, String targetType, String targetId);

    void deleteTemporaryMedia(UUID userId, UUID mediaId);

    /**
     * Deletes an asset from Cloudinary using the exact resource type.
     *
     * @param storageKey   Cloudinary public_id
     * @param resourceType Cloudinary resource_type ("image", "video", "raw")
     * @return {@code true} if deleted or already absent ("not found"), {@code false} on transient failure
     */
    boolean destroyOnCloudinary(String storageKey, String resourceType);
}
