package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.AdminPermission;
import java.util.Set;

public record UpdateAdminPermissionsRequest(
        Set<AdminPermission> permissions
) {}
