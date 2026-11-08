package com.github.gcolin.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.platform.Config;
import java.util.EnumSet;
import java.util.Set;

public class RoleResolver {

    private final Config config;
    private final UserAuthorizationDao userAuthorizationDao;
    private final Cache<String, Boolean> roleCache;

    public RoleResolver(Config config, UserAuthorizationDao userAuthorizationDao, Cache<String, Boolean> roleCache) {
        this.config = config;
        this.userAuthorizationDao = userAuthorizationDao;
        this.roleCache = roleCache;
    }

    public boolean hasRole(String email, RoleCode required) {
        if (email == null || email.isBlank() || required == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (config.getAdmins().contains(normalizedEmail)) {
            return true;
        }
        // Org super-user is treated as ADMIN for the current organisation only (not cached).
        if (isCurrentOrgSuperUser(normalizedEmail)) {
            return RoleCode.satisfies(EnumSet.of(RoleCode.ADMIN), required);
        }
        String cacheKey = normalizedEmail + "|" + required.name();
        Boolean cached = roleCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        Set<RoleCode> granted = userAuthorizationDao.findActiveGlobalRoles(normalizedEmail);
        boolean allowed = RoleCode.satisfies(granted, required);
        roleCache.put(cacheKey, allowed);
        return allowed;
    }

    public boolean hasAnyRole(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (config.getAdmins().contains(normalizedEmail)) {
            return true;
        }
        if (isCurrentOrgSuperUser(normalizedEmail)) {
            return true;
        }
        return !userAuthorizationDao.findActiveGlobalRoles(normalizedEmail).isEmpty();
    }

    public Set<RoleCode> activeRoles(String email) {
        if (email == null || email.isBlank()) {
            return Set.of();
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (config.getAdmins().contains(normalizedEmail)) {
            return EnumSet.allOf(RoleCode.class);
        }
        if (isCurrentOrgSuperUser(normalizedEmail)) {
            return EnumSet.of(RoleCode.ADMIN);
        }
        return userAuthorizationDao.findActiveGlobalRoles(normalizedEmail);
    }

    /** True when {@code email} is the current organisation's {@code org.superUserEmail}. */
    static boolean isCurrentOrgSuperUser(String normalizedEmail) {
        Organisation org = OrgScope.currentOrg();
        if (org == null || normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        String superUserEmail = org.getSuperUserEmail();
        return superUserEmail != null && normalizedEmail.equals(superUserEmail.trim().toLowerCase());
    }
}
