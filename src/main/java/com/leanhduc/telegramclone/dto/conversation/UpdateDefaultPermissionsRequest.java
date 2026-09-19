package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.MemberPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateDefaultPermissionsRequest(
        @NotNull(message = "Permissions set is required")
        Set<MemberPermission> permissions
) {}
