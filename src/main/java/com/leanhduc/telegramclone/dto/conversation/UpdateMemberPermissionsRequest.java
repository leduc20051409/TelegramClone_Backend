package com.leanhduc.telegramclone.dto.conversation;

import com.leanhduc.telegramclone.model.enums.MemberPermission;
import java.util.Set;

public record UpdateMemberPermissionsRequest(
        Set<MemberPermission> permissions
) {}
