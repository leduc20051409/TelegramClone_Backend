package com.leanhduc.telegramclone.dto.message;

import com.leanhduc.telegramclone.dto.media.MediaAttachmentDto;
import com.leanhduc.telegramclone.model.Media;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DiscussionMediaContext(
        List<UUID> mediaIds,
        Map<UUID, Media> mediaById,
        List<MediaAttachmentDto> mediaDtos
) {
    public static DiscussionMediaContext empty() {
        return new DiscussionMediaContext(List.of(), Map.of(), List.of());
    }

    public boolean hasMedia() {
        return mediaIds != null && !mediaIds.isEmpty();
    }
}
