package com.leanhduc.telegramclone.service.conversation;

import com.leanhduc.telegramclone.model.ConversationMember;
import com.leanhduc.telegramclone.model.enums.AdminPermission;
import com.leanhduc.telegramclone.model.enums.ConversationRole;
import com.leanhduc.telegramclone.model.enums.MemberPermission;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PermissionService implements IPermissionService {

    /**
     * Check if a member has a specific member permission.
     * Owners and Admins automatically bypass member restrictions.
     */
    public boolean hasMemberPermission(ConversationMember member, MemberPermission permission) {
        if (member == null || member.getLeftAt() != null) {
            return false;
        }

        // Owners and Admins have full member permissions
        if (member.getRole() == ConversationRole.OWNER || member.getRole() == ConversationRole.ADMIN) {
            return true;
        }

        // For regular members, check custom permissions or fallback to conversation defaults
        Set<MemberPermission> memberPermissions = member.getMemberPermissions();
        if (memberPermissions == null || memberPermissions.isEmpty()) {
            Set<MemberPermission> defaultPermissions = member.getConversation().getDefaultMemberPermissions();
            return defaultPermissions != null && defaultPermissions.contains(permission);
        }

        return memberPermissions.contains(permission);
    }

    /**
     * Check if a member has a specific admin permission.
     * Owner has all admin permissions automatically.
     */
    public boolean hasAdminPermission(ConversationMember member, AdminPermission permission) {
        if (member == null || member.getLeftAt() != null) {
            return false;
        }

        // Owner has all admin permissions
        if (member.getRole() == ConversationRole.OWNER) {
            return true;
        }

        // Non-admins cannot have admin permissions
        if (member.getRole() != ConversationRole.ADMIN) {
            return false;
        }

        Set<AdminPermission> adminPermissions = member.getAdminPermissions();
        return adminPermissions != null && adminPermissions.contains(permission);
    }

    /**
     * Validate whether an actor can assign a set of admin permissions to another member.
     * Admin with ADD_ADMINS cannot grant permissions beyond what they themselves possess.
     */
    public boolean canManageAdminPermissions(ConversationMember actor, Set<AdminPermission> requestedPermissions) {
        if (actor == null || actor.getLeftAt() != null) {
            return false;
        }

        // Owner can assign any permissions
        if (actor.getRole() == ConversationRole.OWNER) {
            return true;
        }

        // Must be an ADMIN with ADD_ADMINS permission
        if (actor.getRole() != ConversationRole.ADMIN || !hasAdminPermission(actor, AdminPermission.ADD_ADMINS)) {
            return false;
        }

        // Admin cannot grant permissions they do not possess
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            return true;
        }

        Set<AdminPermission> actorAdminPermissions = actor.getAdminPermissions();
        return actorAdminPermissions != null && actorAdminPermissions.containsAll(requestedPermissions);
    }
}
