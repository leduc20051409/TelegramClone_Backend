package com.leanhduc.telegramclone.service.media;

import com.leanhduc.telegramclone.dto.media.UploadResponseDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public interface IMediaService {
    UploadResponseDto uploadFile(UUID userId, MultipartFile file);

    void deleteTemporaryMedia(UUID userId, UUID mediaId);
}
