package com.github.gcolin.platform.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

    private final PluginRegistry registry = PluginRegistry.load();

    private static Set<String> allExcept(String... disabled) {
        Set<String> enabled = new LinkedHashSet<>(PluginIds.KNOWN);
        List.of(disabled).forEach(enabled::remove);
        return enabled;
    }

    @Test
    void loadsEveryKnownModuleInStartupOrder() {
        assertEquals(PluginIds.KNOWN, registry.ids());
        for (String id : PluginIds.KNOWN) {
            assertNotNull(registry.get(id));
            assertTrue(registry.get(id).labelKey().startsWith("plugin."));
        }
        assertNull(registry.get("nope"));
    }

    @Test
    void serviceLoaderListsTheSameModules() {
        Set<String> ids = new HashSet<>();
        for (FeatureModule module : ServiceLoader.load(FeatureModule.class)) {
            ids.add(module.id());
        }
        assertEquals(new HashSet<>(PluginIds.KNOWN), ids);
    }

    @Test
    void everythingIsAllowedWhenEveryPluginIsEnabled() {
        Set<String> enabled = allExcept();
        for (String path : List.of("/event", "/event/1/desk", "/payment", "/club-register", "/club", "/admin/mail", "/login")) {
            assertTrue(registry.isPathAllowed(path, enabled), path);
        }
    }

    @Test
    void disabledEventsBlockEventPaths() {
        Set<String> enabled = allExcept(PluginIds.EVENTS);
        for (String path : List.of(
                "/event",
                "/event/5",
                "/events",
                "/myevents",
                "/eventcollection/3",
                "/eventgroup",
                "/admin/events",
                "/playersubscription-admin",
                "/dashboard")) {
            assertFalse(registry.isPathAllowed(path, enabled), path);
        }
        assertTrue(registry.isPathAllowed("/payment", enabled));
        assertTrue(registry.isPathAllowed("/club-register", enabled));
        assertTrue(registry.isPathAllowed("/admin", enabled));
    }

    @Test
    void disabledMembershipAndPaymentsBlockTheirPaths() {
        Set<String> noMembership = allExcept(PluginIds.MEMBERSHIP);
        for (String path : List.of("/club-register", "/membership", "/membership-option-admin", "/license-admin")) {
            assertFalse(registry.isPathAllowed(path, noMembership), path);
        }
        assertTrue(registry.isPathAllowed("/event", noMembership));

        Set<String> noPayments = allExcept(PluginIds.PAYMENTS);
        assertFalse(registry.isPathAllowed("/payment", noPayments));
        assertFalse(registry.isPathAllowed("/payment/sim", noPayments));
        assertTrue(registry.isPathAllowed("/event", noPayments));
    }

    @Test
    void longestPrefixOwnsThePath() {
        assertTrue(registry.isPathAllowed("/club-register", allExcept(PluginIds.PLAYER_INDEX)));
        assertFalse(registry.isPathAllowed("/club", allExcept(PluginIds.PLAYER_INDEX)));
        assertFalse(registry.isPathAllowed("/club/search", allExcept(PluginIds.PLAYER_INDEX)));
        assertFalse(registry.isPathAllowed("/club-register", allExcept(PluginIds.MEMBERSHIP)));
    }

    @Test
    void wildcardSegmentsGateTheDesk() {
        Set<String> noDesk = allExcept(PluginIds.DESK);
        assertFalse(registry.isPathAllowed("/event/12/desk", noDesk));
        assertFalse(registry.isPathAllowed("/ws/event/12/desk", noDesk));
        assertTrue(registry.isPathAllowed("/event/12", noDesk));
        assertTrue(registry.isPathAllowed("/event/12/register", noDesk));
        assertTrue(registry.isPathAllowed("/event/12/desk", allExcept()));
    }

    @Test
    void otherPluginsOwnTheirRoutes() {
        assertFalse(registry.isPathAllowed("/admin/mail", allExcept(PluginIds.EMAIL)));
        assertTrue(registry.isPathAllowed("/admin", allExcept(PluginIds.EMAIL)));
        assertFalse(registry.isPathAllowed("/customplayer", allExcept(PluginIds.CUSTOM_PLAYER)));
        assertFalse(registry.isPathAllowed("/player", allExcept(PluginIds.PLAYER_INDEX)));
        assertFalse(registry.isPathAllowed("/database", allExcept(PluginIds.PLAYER_INDEX)));
    }

    @Test
    void unownedAndNullPathsAreAlwaysAllowed() {
        Set<String> none = Set.of();
        assertTrue(registry.isPathAllowed("/login", none));
        assertTrue(registry.isPathAllowed("/statistics", none));
        assertTrue(registry.isPathAllowed("/", none));
        assertTrue(registry.isPathAllowed(null, none));
        assertFalse(registry.isPathAllowed("/event?x=1", none));
    }

    @Test
    void adminTilesFollowEnabledPlugins() {
        assertTrue(registry.adminTiles(Set.of()).isEmpty());
        List<AdminTile> tiles = registry.adminTiles(Set.of(PluginIds.EVENTS));
        assertEquals(1, tiles.size());
        assertEquals(PluginIds.EVENTS, tiles.get(0).requiredPluginId());
    }

    @Test
    void ofKeepsTheFirstModuleOfAnId() {
        FeatureModule first = new FeatureModule() {
            @Override
            public String id() {
                return "x";
            }

            @Override
            public String labelKey() {
                return "plugin.x";
            }

            @Override
            public java.util.Collection<String> pathPrefixes() {
                return List.of("/x");
            }
        };
        PluginRegistry custom = PluginRegistry.of(List.of(first, first));
        assertEquals(List.of("x"), custom.ids());
        assertFalse(custom.isPathAllowed("/x/y", Set.of()));
        assertTrue(custom.isPathAllowed("/x/y", Set.of("x")));
    }

    @Test
    void hookBusDefaultsToNoopMail() throws Exception {
        HookBus bus = new HookBus();
        bus.mailSender().send("a@b.test", "s", "b");
        StringBuilder sent = new StringBuilder();
        bus.setMailSender((to, subject, body) -> sent.append(to));
        bus.mailSender().send("a@b.test", "s", "b");
        assertEquals("a@b.test", sent.toString());
        bus.setMailSender(null);
        assertEquals(MailSender.NOOP, bus.mailSender());
    }

    @Test
    void moduleHooksAreRegistered() {
        HookBus bus = new HookBus();
        registry.modules().forEach(module -> module.registerHooks(bus));
        assertEquals(1, bus.playerSearchContributors().size());
        assertEquals(1, bus.registrationPlayerSources().size());
        assertEquals(
                Set.of("event", "membership"),
                new HashSet<>(bus.payables().stream().map(Payable::kind).toList()));
        List<String> completed = new java.util.ArrayList<>();
        bus.addPaymentCompletedListener((kind, id) -> completed.add(kind + ":" + id));
        bus.firePaymentCompleted("event", 7);
        assertEquals(List.of("event:7"), completed);
    }
}