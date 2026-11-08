package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.Page;
import org.junit.jupiter.api.Test;

class OrgFeatureFilterTest {

    @Test
    void blocksEventPathsWhenEventsDisabled() {
        Page page = enabledAll();
        page.setEventsEnabled(false);
        assertTrue(OrgFeatureFilter.isBlocked("/event", page));
        assertTrue(OrgFeatureFilter.isBlocked("/event/my", page));
        assertTrue(OrgFeatureFilter.isBlocked("/admin/events", page));
        assertTrue(OrgFeatureFilter.isBlocked("/playersubscription-admin", page));
        assertTrue(OrgFeatureFilter.isBlocked("/dashboard", page));
        assertFalse(OrgFeatureFilter.isBlocked("/payment", page));
        assertFalse(OrgFeatureFilter.isBlocked("/club-register", page));
    }

    @Test
    void blocksMembershipPathsWhenMembershipsDisabled() {
        Page page = enabledAll();
        page.setMembershipsEnabled(false);
        assertTrue(OrgFeatureFilter.isBlocked("/club-register", page));
        assertTrue(OrgFeatureFilter.isBlocked("/membership", page));
        assertTrue(OrgFeatureFilter.isBlocked("/membership-option-admin", page));
        assertTrue(OrgFeatureFilter.isBlocked("/license-admin", page));
        assertFalse(OrgFeatureFilter.isBlocked("/event", page));
    }

    @Test
    void blocksPaymentPathsWhenPaymentsDisabled() {
        Page page = enabledAll();
        page.setPaymentsEnabled(false);
        assertTrue(OrgFeatureFilter.isBlocked("/payment", page));
        assertTrue(OrgFeatureFilter.isBlocked("/payment/sim", page));
        assertFalse(OrgFeatureFilter.isBlocked("/event", page));
    }

    @Test
    void statisticsRequiresEventsOrPayments() {
        Page page = enabledAll();
        page.setEventsEnabled(false);
        page.setPaymentsEnabled(false);
        assertTrue(OrgFeatureFilter.isBlocked("/statistics", page));

        page.setPaymentsEnabled(true);
        assertFalse(OrgFeatureFilter.isBlocked("/statistics", page));
    }

    @Test
    void allowsPathsWhenFeaturesEnabled() {
        Page page = enabledAll();
        assertFalse(OrgFeatureFilter.isBlocked("/event", page));
        assertFalse(OrgFeatureFilter.isBlocked("/membership", page));
        assertFalse(OrgFeatureFilter.isBlocked("/payment", page));
        assertFalse(OrgFeatureFilter.isBlocked("/admin", page));
        assertFalse(OrgFeatureFilter.isBlocked("/admin/organisations", page));
    }

    private static Page enabledAll() {
        Page page = new Page();
        page.setEventsEnabled(true);
        page.setMembershipsEnabled(true);
        page.setPaymentsEnabled(true);
        return page;
    }
}
