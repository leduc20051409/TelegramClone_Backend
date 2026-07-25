package com.leanhduc.telegramclone.service.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService implements IMediaService {

    private final Cloudinary cloudinary;
    private final MediaRepository mediaRepository;

    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp",
            "mp4", "webm",
            "pdf", "doc", "docx");

    @Override
    public UploadResponseDto uploadFile(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        log.info("User {} uploading file: {}", userId, originalFilename);

        if (size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        try {
            String resourceType = getResourceType(contentType);
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", resourceType,
                            "folder", "telegram-clone/" + userId,
                            "public_id", originalFilename.replaceAll("\\.[^.]+$", "")));
            String secureUrl = (String) result.get("secure_url");
            String storageKey = (String) result.get("public_id");
            Integer width = toInteger(result.get("width"));
            Integer height = toInteger(result.get("height"));
            BigDecimal durationSeconds = toBigDecimal(result.get("duration"));

            Media media = Media.builder()
                    .ownerId(userId)
                    .storageKey(storageKey)
                    .url(secureUrl)
                    .resourceType(resourceType)
                    .mimeType(contentType)
                    .fileName(originalFilename)
                    .fileSize(size)
                    .width(width)
                    .height(height)
                    .durationSeconds(durationSeconds)
                    .status(MediaStatus.TEMP)
                    .build();
            media = mediaRepository.save(media);
            return new UploadResponseDto(
                    media.getId(),
                    secureUrl,
                    contentType,
                    size,
                    originalFilename);
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary for user: {}", userId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public UploadResponseDto uploadAvatar(UUID ownerId, MultipartFile file, String targetType, String targetId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        String contentType = file.getContentType();
        long size = file.getSize();

        if (size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String folder;
        String filename;
        if ("CONVERSATION".equalsIgnoreCase(targetType) || "GROUP".equalsIgnoreCase(targetType)
                || "CHANNEL".equalsIgnoreCase(targetType)) {
            if (targetId == null || targetId.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            folder = "telegram-clone/" + targetId;
            filename = "conv_avatar";
        } else {
            String resolvedUserId = (targetId != null && !targetId.isBlank()) ? targetId : ownerId.toString();
            folder = "telegram-clone/" + resolvedUserId;
            filename = "user_avatar";
        }

        try {
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "folder", folder,
                            "public_id", filename,
                            "overwrite", true,
                            "invalidate", true));
            String secureUrl = (String) result.get("secure_url");
            String storageKey = (String) result.get("public_id");
            Integer width = toInteger(result.get("width"));
            Integer height = toInteger(result.get("height"));

            String cacheBustedUrl = secureUrl.contains("?")
                    ? secureUrl + "&t=" + System.currentTimeMillis()
                    : secureUrl + "?t=" + System.currentTimeMillis();

            Media media = mediaRepository.findByStorageKey(storageKey)
                    .orElseGet(() -> Media.builder()
                            .ownerId(ownerId)
                            .storageKey(storageKey)
                            .resourceType("image")
                            .mimeType(contentType)
                            .fileName(folder + "/" + filename)
                            .status(MediaStatus.ACTIVE)
                            .build());

            media.setOwnerId(ownerId);
            media.setUrl(cacheBustedUrl);
            media.setMimeType(contentType);
            media.setFileSize(size);
            media.setWidth(width);
            media.setHeight(height);
            media.setStatus(MediaStatus.ACTIVE);

            media = mediaRepository.save(media);

            return new UploadResponseDto(
                    media.getId(),
                    cacheBustedUrl,
                    contentType,
                    size,
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
        } catch (IOException e) {
            log.error("Failed to upload avatar to Cloudinary for owner: {}", ownerId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void deleteTemporaryMedia(UUID userId, UUID mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
        if (!userId.equals(media.getOwnerId()) || media.getStatus() != MediaStatus.TEMP) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_ACCESSIBLE);
        }
        try {
            cloudinary.uploader().destroy(media.getStorageKey(),
                    ObjectUtils.asMap("resource_type", media.getResourceType()));
        } catch (IOException e) {
            log.error("Failed to delete media from Cloudinary: {}", mediaId, e);
            throw new BusinessException(ErrorCode.DELETE_MEDIA_FAILED);
        }
        mediaRepository.delete(media);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private String getResourceType(String contentType) {
        if (contentType == null)
            return "raw";
        if (contentType.startsWith("image"))
            return "image";
        if (contentType.startsWith("video"))
            return "video";
        return "raw"; // document, pdf, docx
    }
}
