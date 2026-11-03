package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.Config;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrganisationBootstrapTest {

    @Test
    void clubInvoiceStripeKeysAreDetected() {
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("title"));
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("org.email"));
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("invoice.seller.name"));
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("stripe.public"));
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("mail.USER_NAME"));
        assertTrue(OrgConfig.isClubInvoiceStripeSettingsKey("ui.primaryColor"));
        assertFalse(OrgConfig.isClubInvoiceStripeSettingsKey("oauth.clientId"));
        assertFalse(OrgConfig.isClubInvoiceStripeSettingsKey("jwt.key"));
        assertFalse(OrgConfig.isClubInvoiceStripeSettingsKey("baseurl"));
        assertFalse(OrgConfig.isClubInvoiceStripeSettingsKey("db.host"));
    }

    @Test
    void backfillCopiesBlankOrgFieldsFromPlatform() {
        Config config = new Config();
        config.getProperties().setProperty("title", "From Platform");
        config.getProperties().setProperty("org.email", "club@example.org");
        config.getProperties().setProperty("stripe.simuled", "true");
        config.getProperties().setProperty("jwt.key", "keep-me");
        config.getProperties().setProperty("oauth.clientId", "cid");

        Organisation org = new Organisation();
        org.setSlug("default");
        org.setBaseUrl("http://localhost:8080");
        org.setName("Existing");
        org.setSuperUserEmail("admin@example.org");

        OrganisationBootstrap bootstrap = new OrganisationBootstrap(config, null);
        Set<String> migrated = bootstrap.backfillFromPlatform(org);

        assertTrue(migrated.contains("title"));
        assertTrue(migrated.contains("org.email"));
        assertTrue(migrated.contains("stripe.simuled"));
        assertFalse(migrated.contains("jwt.key"));
        assertFalse(migrated.contains("oauth.clientId"));
        assertEquals("From Platform", new OrgConfig(org).getProperty("title"));
        assertEquals("club@example.org", new OrgConfig(org).getProperty("org.email"));
        assertEquals("true", new OrgConfig(org).getProperty("stripe.simuled"));
    }

    @Test
    void seedPutsClubInvoiceStripeIntoOrganisation() {
        Config config = new Config();
        config.getProperties().setProperty("baseurl", "http://localhost:8080");
        config.getProperties().setProperty("title", "Seed Club");
        config.getProperties().setProperty("org.name", "Seed Club");
        config.getProperties().setProperty("org.email", "seed@example.org");
        config.getProperties().setProperty("invoice.seller.name", "Seller");
        config.getProperties().setProperty("stripe.simuled", "true");
        config.getProperties().setProperty("admins", "admin@example.org");

        Organisation org = OrgConfig.seed(config.getProperties());
        OrgConfig view = new OrgConfig(org);

        assertEquals("default", org.getSlug());
        assertEquals("Seed Club", org.getName());
        assertEquals("Seed Club", view.getProperty("title"));
        assertEquals("seed@example.org", view.getProperty("org.email"));
        assertEquals("Seller", view.getProperty("invoice.seller.name"));
        assertEquals("true", view.getProperty("stripe.simuled"));
    }

    @Test
    void seedWithTestmodeFillsOrganisationDefaultsWithoutClubKeysInParams() {
        Config config = new Config();
        config.getProperties().setProperty("baseurl", "http://localhost:8080");
        config.getProperties().setProperty("testmode", "true");
        config.getProperties().setProperty("admins", "admin@test.com");

        Organisation org = OrgConfig.seed(config.getProperties());
        OrgConfig view = new OrgConfig(org);

        assertEquals("Ticket Chess Test", org.getName());
        assertTrue(org.isStripeSimuled());
        assertEquals("true", view.getProperty("stripe.simuled"));
        assertEquals("admin@test.com", view.getProperty("membership.notif.emails"));
        assertEquals("admin@test.com", view.getProperty("org.email"));
        assertEquals("Ticket Chess Test", view.getProperty("title"));
    }
}
