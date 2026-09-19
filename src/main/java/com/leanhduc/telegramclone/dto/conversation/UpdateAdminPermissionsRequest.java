package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.AdminPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateAdminPermissionsRequest(
        @NotNull(message = "Permissions set is required")
        Set<AdminPermission> permissions
) {}
