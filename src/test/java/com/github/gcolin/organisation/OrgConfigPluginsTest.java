package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.plugin.PluginIds;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OrgConfigPluginsTest {

    private static Set<String> allExcept(String... disabled) {
        Set<String> enabled = new LinkedHashSet<>(PluginIds.KNOWN);
        List.of(disabled).forEach(enabled::remove);
        return enabled;
    }

    @Test
    void everyKnownPluginIsEnabledWhenNothingIsConfigured() {
        OrgConfig config = new OrgConfig(new Organisation());
        assertEquals(allExcept(), config.enabledPlugins());
        assertNull(config.getProperty(OrgConfig.KEY_PLUGINS));
    }

    @Test
    void featureFlagsDrivePluginsWhenNoListIsStored() {
        OrgConfig config = new OrgConfig(new Organisation());
        config.setProperty(OrgConfig.FEATURE_EVENTS, "false");
        assertEquals(allExcept(PluginIds.EVENTS, PluginIds.DESK), config.enabledPlugins());
        assertFalse(config.isPluginEnabled(PluginIds.EVENTS));
        assertFalse(config.isPluginEnabled(PluginIds.DESK));
        assertTrue(config.isPluginEnabled(PluginIds.EMAIL));

        config.setProperty(OrgConfig.FEATURE_MEMBERSHIPS, "false");
        config.setProperty(OrgConfig.FEATURE_PAYMENTS, "false");
        assertEquals(
                Set.of(PluginIds.EMAIL, PluginIds.PLAYER_INDEX, PluginIds.CUSTOM_PLAYER), config.enabledPlugins());

        config.setProperty(OrgConfig.FEATURE_EVENTS, "true");
        assertTrue(config.isPluginEnabled(PluginIds.EVENTS));
        assertTrue(config.isPluginEnabled(PluginIds.DESK));
    }

    @Test
    void explicitListWinsOverFeatureFlags() {
        OrgConfig config = new OrgConfig(new Organisation());
        config.setProperty(OrgConfig.FEATURE_EVENTS, "false");
        config.setPlugins(List.of(PluginIds.EVENTS, PluginIds.EMAIL));
        assertEquals(Set.of(PluginIds.EVENTS, PluginIds.EMAIL), config.enabledPlugins());
        assertTrue(config.isPluginEnabled(PluginIds.EVENTS));
        assertFalse(config.isPluginEnabled(PluginIds.PAYMENTS));
        assertEquals("events,email", config.getProperty(OrgConfig.KEY_PLUGINS));
        assertTrue(config.isExplicitlySet(OrgConfig.KEY_PLUGINS));
    }

    @Test
    void listIsStoredAsJsonArray() {
        Organisation org = new Organisation();
        OrgConfig config = new OrgConfig(org);
        config.setPlugins(List.of("events", "payments", "events"));
        JSONObject json = new JSONObject(org.getSettingsJson());
        assertEquals(2, json.getJSONArray("plugins").length());
        assertEquals("events", json.getJSONArray("plugins").getString(0));
        assertEquals("events,payments", config.toMap(false).get(OrgConfig.KEY_PLUGINS));
    }

    @Test
    void setPropertyAcceptsCsvEmptyAndNull() {
        OrgConfig config = new OrgConfig(new Organisation());
        config.setProperty(OrgConfig.KEY_PLUGINS, " events , membership,, ");
        assertEquals(Set.of("events", "membership"), config.enabledPlugins());

        config.setProperty(OrgConfig.KEY_PLUGINS, "");
        assertTrue(config.enabledPlugins().isEmpty());
        assertEquals("", config.getProperty(OrgConfig.KEY_PLUGINS));

        config.setProperty(OrgConfig.KEY_PLUGINS, null);
        assertNull(config.getProperty(OrgConfig.KEY_PLUGINS));
        assertEquals(allExcept(), config.enabledPlugins());
    }

    @Test
    void pluginsKeyIsOrgScopedAndSurvivesSettingsRoundTrip() {
        assertTrue(OrgConfig.isOrgScoped(OrgConfig.KEY_PLUGINS));
        Organisation org = new Organisation();
        org.setName("Club");
        new OrgConfig(org).setPlugins(List.of("events", "desk"));
        OrganisationSettings.pack(org);
        OrganisationSettings.unpack(org);
        assertEquals(Set.of("events", "desk"), new OrgConfig(org).enabledPlugins());
    }

    @Test
    void legacyStringValueIsAccepted() {
        Organisation org = new Organisation();
        org.setSettingsJson("{\"plugins\":\"events,payments\"}");
        assertEquals(Set.of("events", "payments"), new OrgConfig(org).enabledPlugins());
    }
}