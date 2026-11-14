package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.auth.LoginApi;
import com.github.gcolin.desk.EventDeskApi;
import com.github.gcolin.event.EventApi;
import com.github.gcolin.membership.MembershipApi;
import com.github.gcolin.organisation.OrganisationAdminApi;
import com.github.gcolin.payment.PaymentApi;
import com.github.gcolin.player.CustomPlayerApi;
import com.github.gcolin.player.PlayerApi;
import com.github.gcolin.platform.plugin.PluginRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TicketChessApplicationTest {

    @AfterEach
    void clear() {
        AppContext.clearTestInstance();
    }

    @Test
    void registersCoreOnlyWhenAppContextIsNotReady() {
        AppContext.clearTestInstance();
        TicketChessApplication application = new TicketChessApplication();
        assertTrue(application.getClasses().contains(LoginApi.class));
        assertTrue(application.getClasses().contains(HomeApi.class));
        assertTrue(application.getClasses().contains(SignatureApi.class));
        assertFalse(application.getClasses().contains(EventApi.class));
    }

    @Test
    void registersEveryPluginWhenAppContextIsReady() {
        AppContext app = AppContext.initForTest();
        TicketChessApplication application = new TicketChessApplication();
        for (Class<?> api : new Class<?>[] {
            LoginApi.class,
            AdminApi.class,
            OrganisationAdminApi.class,
            EventApi.class,
            MembershipApi.class,
            PaymentApi.class,
            CustomPlayerApi.class,
            PlayerApi.class,
            EventDeskApi.class
        }) {
            assertTrue(application.getClasses().contains(api), api.getName());
        }
        assertNotNull(app.pluginRegistry());
    }

    @Test
    void usesTheInstalledRegistry() {
        AppContext app = AppContext.initForTest();
        PluginRegistry registry = PluginRegistry.load();
        registry.install(app);
        new TicketChessApplication();
        assertSame(registry, app.pluginRegistry());
        assertEquals(registry.ids(), app.pluginRegistry().ids());
    }
}
