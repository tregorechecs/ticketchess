package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.github.gcolin.platform.plugin.PluginIds;
import jakarta.servlet.ServletContext;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigPluginsTest {

    @TempDir
    Path tempConfigDir;

    private String previousConfigDir;
    private Config config;

    @BeforeEach
    void setUp() {
        previousConfigDir = System.getProperty("CONFIG_DIR");
        System.setProperty("CONFIG_DIR", tempConfigDir.toAbsolutePath().toString());
        config = new Config();
        config.init(mock(ServletContext.class));
    }

    @AfterEach
    void tearDown() {
        if (previousConfigDir == null) {
            System.clearProperty("CONFIG_DIR");
        } else {
            System.setProperty("CONFIG_DIR", previousConfigDir);
        }
    }

    @Test
    void pageFlagsAreEnabledByDefault() {
        Page page = config.pageForRequest();
        assertTrue(page.isEventsEnabled());
        assertTrue(page.isMembershipsEnabled());
        assertTrue(page.isPaymentsEnabled());
        assertTrue(page.isClubRegisterEnabled());
        assertTrue(config.enabledPlugins().containsAll(PluginIds.KNOWN));
    }

    @Test
    void pageFlagsFollowFeatureFlagsWhenNoPluginListIsStored() {
        config.getProperties().setProperty("feature.events", "false");
        config.getProperties().setProperty("feature.payments", "false");
        Page page = config.pageForRequest();
        assertFalse(page.isEventsEnabled());
        assertTrue(page.isMembershipsEnabled());
        assertFalse(page.isPaymentsEnabled());
    }

    @Test
    void pageFlagsFollowAnExplicitPluginList() {
        config.getProperties().setProperty("plugins", "payments,email");
        Page page = config.pageForRequest();
        assertFalse(page.isEventsEnabled());
        assertFalse(page.isMembershipsEnabled());
        assertFalse(page.isClubRegisterEnabled());
        assertTrue(page.isPaymentsEnabled());
        assertEquals(Set.of("payments", "email"), config.enabledPlugins());
    }
}