package com.example.demo.security;

/**
 * Admin permission tiers. Stored as a string on the user row; checked by
 * {@link AdminAccessGuard} on every privileged call.
 *
 * <h3>Capability matrix</h3>
 * <pre>
 *                          SUPPORT  MODERATOR  SUPER_ADMIN
 *  view dashboards            ✔         ✔           ✔
 *  read user metadata         ✔         ✔           ✔
 *  delete posts / comments    ✘         ✔           ✔
 *  block users                ✘         ✔           ✔
 *  delete users               ✘         ✘           ✔
 *  grant / revoke admin       ✘         ✘           ✔
 *  rotate encryption key      ✘         ✘           ✔
 * </pre>
 */
public enum AdminRole {
    SUPPORT,
    MODERATOR,
    SUPER_ADMIN;

    public boolean canModerate() {
        return this == MODERATOR || this == SUPER_ADMIN;
    }

    public boolean canDestroyUsers() {
        return this == SUPER_ADMIN;
    }

    public boolean canManageAdmins() {
        return this == SUPER_ADMIN;
    }
}
