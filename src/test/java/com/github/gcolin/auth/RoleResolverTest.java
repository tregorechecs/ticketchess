package com.github.gcolin.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.TestContext;
import java.sql.Connection;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RoleResolverTest {

    @AfterEach
    void tearDown() {
        RequestContext.close();
    }

    @Test
    void currentOrgSuperUserSeesAdminMenuAndAdminRole() {
        Config config = mock(Config.class);
        when(config.getAdmins()).thenReturn(Set.of());
        UserAuthorizationDao dao = mock(UserAuthorizationDao.class);
        when(dao.findActiveGlobalRoles("gicolin01@gmail.com")).thenReturn(Set.of());
        @SuppressWarnings("unchecked")
        Cache<String, Boolean> cache = mock(Cache.class);

        RoleResolver resolver = new RoleResolver(config, dao, cache);

        TestContext.open((Connection) null);
        Organisation org = new Organisation();
        org.setId(2);
        org.setSuperUserEmail("gicolin01@gmail.com");
        RequestContext.require().setOrganisation(org);

        assertTrue(resolver.hasAnyRole("gicolin01@gmail.com"));
        assertTrue(resolver.hasRole("gicolin01@gmail.com", RoleCode.ADMIN));
        assertTrue(resolver.hasRole("gicolin01@gmail.com", RoleCode.ARBITRE));
        assertFalse(resolver.hasAnyRole("other@example.org"));
    }

    @Test
    void superUserOfOtherOrgDoesNotGetRoles() {
        Config config = mock(Config.class);
        when(config.getAdmins()).thenReturn(Set.of());
        UserAuthorizationDao dao = mock(UserAuthorizationDao.class);
        when(dao.findActiveGlobalRoles("gicolin01@gmail.com")).thenReturn(Set.of());
        @SuppressWarnings("unchecked")
        Cache<String, Boolean> cache = mock(Cache.class);

        RoleResolver resolver = new RoleResolver(config, dao, cache);

        TestContext.open((Connection) null);
        Organisation org = new Organisation();
        org.setId(1);
        org.setSuperUserEmail("someone-else@example.org");
        RequestContext.require().setOrganisation(org);

        assertFalse(resolver.hasAnyRole("gicolin01@gmail.com"));
        assertFalse(resolver.hasRole("gicolin01@gmail.com", RoleCode.ADMIN));
    }
}
