package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OrganisationSettingsTest {

    @Test
    void packUnpackRoundTripPreservesNameOauthStripeAndExistingJsonKeys() {
        Organisation org = new Organisation();
        org.setName("Tournois Test");
        org.setSuperUserEmail("admin@example.org");
        org.setStripePublic("pk_test");
        org.setStripeSecret("sk_test");
        org.setStripeKeyprefix("tc");
        org.setStripeSimuled(true);
        org.setStripeCardEvents(false);
        org.setOauthClientId("client-id");
        org.setOauthClientSecret("client-secret");
        org.setOauthAuthorizationUrl("https://auth.example/authorize");
        org.setMailUserName("mail@example.org");
        org.setSettingsJson("{\"feature.events\":\"true\",\"invoice.seller.name\":\"Seller\"}");

        OrganisationSettings.pack(org);

        JSONObject packed = new JSONObject(org.getSettingsJson());
        assertEquals("Tournois Test", packed.getString("org.name"));
        assertEquals("admin@example.org", packed.getString(OrgConfig.KEY_SUPER_USER_EMAIL));
        assertEquals("pk_test", packed.getString("stripe.public"));
        assertEquals("sk_test", packed.getString("stripe.secret"));
        assertEquals("true", packed.getString("stripe.simuled"));
        assertEquals("false", packed.getString("stripe.card.events"));
        assertEquals("client-id", packed.getString("oauth.clientId"));
        assertEquals("https://auth.example/authorize", packed.getString("oauth.authorizationUrl"));
        assertEquals("true", packed.getString("feature.events"));
        assertEquals("Seller", packed.getString("invoice.seller.name"));

        Organisation loaded = new Organisation();
        loaded.setSettingsJson(org.getSettingsJson());
        OrganisationSettings.unpack(loaded);

        assertEquals("Tournois Test", loaded.getName());
        assertEquals("admin@example.org", loaded.getSuperUserEmail());
        assertEquals("pk_test", loaded.getStripePublic());
        assertEquals("sk_test", loaded.getStripeSecret());
        assertEquals("tc", loaded.getStripeKeyprefix());
        assertTrue(loaded.isStripeSimuled());
        assertFalse(loaded.isStripeCardEvents());
        assertTrue(loaded.isStripeCardMemberships());
        assertEquals("client-id", loaded.getOauthClientId());
        assertEquals("client-secret", loaded.getOauthClientSecret());
        assertEquals("https://auth.example/authorize", loaded.getOauthAuthorizationUrl());
        assertEquals("mail@example.org", loaded.getMailUserName());
        assertEquals("true", new OrgConfig(loaded).getProperty("feature.events"));
        assertEquals("Seller", new OrgConfig(loaded).getProperty("invoice.seller.name"));
    }

    @Test
    void mergeLegacyColumnsDoesNotOverwriteExistingJson() {
        String existing = "{\"org.name\":\"FromJson\",\"stripe.public\":\"pk_json\"}";
        OrganisationSettings.LegacyColumns legacy = new OrganisationSettings.LegacyColumns(
                "FromColumn",
                "admin@example.org",
                "pk_column",
                "sk_column",
                null,
                true,
                null,
                null,
                null,
                null,
                "oauth-col",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        String merged = OrganisationSettings.mergeLegacyColumns(existing, legacy);
        JSONObject json = new JSONObject(merged);

        assertEquals("FromJson", json.getString("org.name"));
        assertEquals("pk_json", json.getString("stripe.public"));
        assertEquals("admin@example.org", json.getString(OrgConfig.KEY_SUPER_USER_EMAIL));
        assertEquals("sk_column", json.getString("stripe.secret"));
        assertEquals("true", json.getString("stripe.simuled"));
        assertEquals("oauth-col", json.getString("oauth.clientId"));
    }

    @Test
    void packClearsBlankTypedKeysFromJson() {
        Organisation org = new Organisation();
        org.setName("Keep");
        org.setStripePublic("");
        org.setSettingsJson("{\"stripe.public\":\"old\",\"org.name\":\"old\"}");

        OrganisationSettings.pack(org);

        JSONObject packed = new JSONObject(org.getSettingsJson());
        assertEquals("Keep", packed.getString("org.name"));
        assertFalse(packed.has("stripe.public"));
        assertNull(OrganisationSettings.mergeLegacyColumns(null, emptyLegacy()));
    }

    @Test
    void normalizeJsonFoldsKeycloakHostingAndDropsDuplicateInvoiceKeys() {
        String raw =
                """
                {
                  "org.name":"Trégor Echecs",
                  "org.email":"club@example.org",
                  "org.address":"1 place Ernest Laurent 22300 LANNION",
                  "org.hosting.address":"1 place Ernest Laurent 22300 LANNION",
                  "invoice.seller.name":"Trégor Echecs",
                  "invoice.seller.email":"club@example.org",
                  "invoice.seller.address1":"1 place Ernest Laurent",
                  "invoice.seller.siret":"81203256300023",
                  "keycloak.CLIENT_ID":"ticket-test",
                  "keycloak.CLIENT_SECRET":"secret",
                  "keycloak.url":"https://oauth2.example/oauth/authorize",
                  "feature.events":"true"
                }
                """;

        JSONObject normalized = new JSONObject(OrganisationSettings.normalizeJson(raw));

        assertEquals("ticket-test", normalized.getString("oauth.clientId"));
        assertEquals("secret", normalized.getString("oauth.clientSecret"));
        assertEquals("https://oauth2.example/oauth/authorize", normalized.getString("oauth.authorizationUrl"));
        assertFalse(normalized.has("keycloak.CLIENT_ID"));
        assertFalse(normalized.has("keycloak.url"));
        assertFalse(normalized.has("org.hosting.address"));
        assertEquals("1 place Ernest Laurent 22300 LANNION", normalized.getString("org.address"));
        assertFalse(normalized.has("invoice.seller.name"));
        assertFalse(normalized.has("invoice.seller.email"));
        assertEquals("1 place Ernest Laurent", normalized.getString("invoice.seller.address1"));
        assertEquals("81203256300023", normalized.getString("invoice.seller.siret"));
        assertEquals("true", normalized.getString("feature.events"));
    }

    @Test
    void unpackAppliesNormalizedOauthFromKeycloakAliases() {
        Organisation org = new Organisation();
        org.setSettingsJson(
                "{\"keycloak.CLIENT_ID\":\"cid\",\"keycloak.url\":\"https://idp.example/auth\"}");

        OrganisationSettings.unpack(org);

        assertEquals("cid", org.getOauthClientId());
        assertEquals("https://idp.example/auth", org.getOauthAuthorizationUrl());
        JSONObject json = new JSONObject(org.getSettingsJson());
        assertEquals("cid", json.getString("oauth.clientId"));
        assertFalse(json.has("keycloak.CLIENT_ID"));
    }

    private static OrganisationSettings.LegacyColumns emptyLegacy() {
        return new OrganisationSettings.LegacyColumns(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
