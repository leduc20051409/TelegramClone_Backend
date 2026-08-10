package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.MemberPermission;

import java.util.Set;

public interface IPermissionService {
    boolean hasMemberPermission(ConversationMember member, MemberPermission permission);
    boolean hasAdminPermission(ConversationMember member, AdminPermission permission);
    boolean canManageAdminPermissions(ConversationMember actor, Set<AdminPermission> requestedPermissions);
}
