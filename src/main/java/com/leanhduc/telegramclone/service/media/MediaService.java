package com.leanhduc.telegramclone.service.media;

import com.cloudinary.Cloudinary;
import com.leanhduc.telegramclone.aop.LogExecutionTime;
import com.cloudinary.utils.ObjectUtils;
import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import com.leanhduc.telegramclone.model.Conversation;
import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.ConversationMemberId;
import com.leanhduc.telegramclone.model.Media;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationType;
import com.leanhduc.telegramclone.model.enums.MediaStatus;
import com.leanhduc.telegramclone.repository.ConversationMemberRepository;
import com.leanhduc.telegramclone.repository.ConversationRepository;
import com.leanhduc.telegramclone.repository.MediaRepository;
import com.leanhduc.telegramclone.service.conversation.IPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
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
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final IPermissionService permissionService;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;
    private static final long STREAMING_UPLOAD_THRESHOLD_BYTES = 5L * 1024 * 1024;
    private static final int CLOUDINARY_CHUNK_SIZE_BYTES = 6 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp",
            "mp4", "webm", "mov",
            "pdf", "doc", "docx");

    @Override
    @LogExecutionTime(thresholdMs = 1000, description = "Cloudinary file upload")
    public UploadResponseDto uploadFile(UUID userId, MultipartFile file) {
        return uploadFile(userId, file, false);
    }

    @Override
    @LogExecutionTime(thresholdMs = 1000, description = "Cloudinary file upload")
    public UploadResponseDto uploadFile(UUID userId, MultipartFile file, boolean asFile) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE);
        }
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        log.info("User {} uploading file: {} (asFile={})", userId, originalFilename, asFile);

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        long maxAllowedSize = contentType.startsWith("video/") ? MAX_VIDEO_SIZE : MAX_FILE_SIZE;
        if (size > maxAllowedSize) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }

        String normalizedExtension = extension.toLowerCase();
        String resourceType = asFile ? "raw" : getResourceType(contentType);
        String uniquePublicId = buildUniquePublicId(originalFilename, resourceType, normalizedExtension);

        try {
            Map<String, Object> uploadOptions = ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "folder", "telegram-clone/" + userId,
                    "public_id", uniquePublicId);
            Map<String, Object> result;
            if (size > STREAMING_UPLOAD_THRESHOLD_BYTES) {
                try (InputStream inputStream = file.getInputStream()) {
                    result = cloudinary.uploader().uploadLarge(
                            inputStream,
                            uploadOptions,
                            CLOUDINARY_CHUNK_SIZE_BYTES);
                }
            } else {
                result = cloudinary.uploader().upload(file.getBytes(), uploadOptions);
            }
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

        if (size > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        AvatarTarget target = resolveAvatarTarget(ownerId, targetType, targetId);
        String folder = target.folder();
        String filename = target.filename();

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
        if (!destroyOnCloudinary(media.getStorageKey(), media.getResourceType())) {
            throw new BusinessException(ErrorCode.DELETE_MEDIA_FAILED);
        }
        mediaRepository.delete(media);
    }

    @Override
    public boolean destroyOnCloudinary(String storageKey, String resourceType) {
        if (storageKey == null || storageKey.isBlank()) {
            return true;
        }
        String effectiveResourceType = (resourceType == null || resourceType.isBlank()) ? "raw" : resourceType;
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    storageKey,
                    ObjectUtils.asMap("resource_type", effectiveResourceType, "invalidate", true));
            Object resultValue = result != null ? result.get("result") : null;
            String status = resultValue != null ? resultValue.toString() : "";
            if ("ok".equalsIgnoreCase(status) || "not found".equalsIgnoreCase(status)) {
                return true;
            }
            log.warn("Unexpected Cloudinary destroy result '{}' for storageKey={} (resourceType={})",
                    status, storageKey, effectiveResourceType);
            return false;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to delete media from Cloudinary: storageKey={} (resourceType={})",
                    storageKey, effectiveResourceType, e);
            return false;
        }
    }

    private String buildUniquePublicId(String originalFilename, String resourceType, String extension) {
        String rawBaseName = originalFilename != null
                ? originalFilename.replaceAll("\\.[^.]+$", "")
                : "file";
        String sanitizedBaseName = rawBaseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitizedBaseName.isBlank()) {
            sanitizedBaseName = "file";
        }
        if (sanitizedBaseName.length() > 60) {
            sanitizedBaseName = sanitizedBaseName.substring(0, 60);
        }
        String basePublicId = UUID.randomUUID() + "_" + sanitizedBaseName;
        if ("raw".equals(resourceType) && extension != null && !extension.isBlank()) {
            return basePublicId + "." + extension;
        }
        return basePublicId;
    }

    private AvatarTarget resolveAvatarTarget(UUID ownerId, String targetType, String targetId) {
        if ("CONVERSATION".equalsIgnoreCase(targetType)
                || "GROUP".equalsIgnoreCase(targetType)
                || "CHANNEL".equalsIgnoreCase(targetType)) {
            if (targetId == null || targetId.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            UUID conversationId;
            try {
                conversationId = UUID.fromString(targetId.trim());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));

            if (conversation.getType() == ConversationType.PRIVATE) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }

            ConversationMember member = conversationMemberRepository
                    .findById(new ConversationMemberId(conversationId, ownerId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_IN_CONVERSATION));

            if (member.getLeftAt() != null) {
                throw new BusinessException(ErrorCode.NOT_IN_CONVERSATION);
            }

            if (!permissionService.hasAdminPermission(member, AdminPermission.CHANGE_INFO)) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED);
            }

            return new AvatarTarget("telegram-clone/" + conversationId, "conv_avatar");
        }

        if (targetType == null || targetType.isBlank() || "USER".equalsIgnoreCase(targetType)) {
            // Always bind USER avatar uploads to the authenticated Principal, ignoring client targetId
            return new AvatarTarget("telegram-clone/" + ownerId, "user_avatar");
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private record AvatarTarget(String folder, String filename) {
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
