package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrgConfigFeatureTest {

    @Test
    void featureEnabledDefaultsToTrueWhenUnset() {
        assertTrue(OrgConfig.isFeatureEnabled(null));
        assertTrue(OrgConfig.isFeatureEnabled(""));
        assertTrue(OrgConfig.isFeatureEnabled("   "));
        assertTrue(OrgConfig.isFeatureEnabled("true"));
        assertTrue(OrgConfig.isFeatureEnabled("TRUE"));
    }

    @Test
    void featureEnabledIsFalseOnlyWhenExplicitFalse() {
        assertFalse(OrgConfig.isFeatureEnabled("false"));
        assertFalse(OrgConfig.isFeatureEnabled("FALSE"));
        assertFalse(OrgConfig.isFeatureEnabled(" false "));
    }

    @Test
    void featureKeysAreOrgScoped() {
        assertTrue(OrgConfig.isOrgScoped(OrgConfig.FEATURE_MEMBERSHIPS));
        assertTrue(OrgConfig.isOrgScoped(OrgConfig.FEATURE_EVENTS));
        assertTrue(OrgConfig.isOrgScoped(OrgConfig.FEATURE_PAYMENTS));
    }
}
