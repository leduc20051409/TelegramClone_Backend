package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import com.leanhduc.telegramclone.service.media.IMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Media management APIs")
public class MediaController {

    private final IMediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file to cloud storage")
    public ResponseEntity<UploadResponseDto> uploadFile(
            Principal principal,
            @RequestParam("file") MultipartFile file) {
        UUID ownerId = UUID.fromString(principal.getName());
        UploadResponseDto response = mediaService.uploadFile(ownerId, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an avatar (user, group, or channel) to Cloudinary with fixed public_id overwrite")
    public ResponseEntity<UploadResponseDto> uploadAvatar(
            Principal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetType", defaultValue = "USER") String targetType,
            @RequestParam(value = "targetId", required = false) String targetId) {
        UUID ownerId = UUID.fromString(principal.getName());
        UploadResponseDto response = mediaService.uploadAvatar(ownerId, file, targetType, targetId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete a temporary uploaded file")
    public ResponseEntity<Void> deleteTemporaryMedia(
            Principal principal,
            @PathVariable UUID mediaId) {
        UUID ownerId = UUID.fromString(principal.getName());
        mediaService.deleteTemporaryMedia(ownerId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
