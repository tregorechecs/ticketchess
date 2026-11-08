package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.TestDb;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OrganisationDaoSettingsJsonTest {

    @Test
    void persistAndLoadRoundTripThroughSettingsJson() throws Exception {
        DataSource dataSource = TestDb.newDataSource();
        Integer orgId = RequestContext.run(dataSource, () -> {
            Organisation org = new Organisation();
            org.setBaseUrl("http://localhost:8080");
            org.setName("Tournois Test");
            org.setSuperUserEmail("admin@example.org");
            org.setStripePublic("pk_live");
            org.setOauthClientId("cid");
            org.setOauthAuthorizationUrl("https://sso.example/auth");
            org.setActive(true);

            OrganisationDao dao = new OrganisationDao();
            dao.persist(org);
            assertNotNull(org.getId());

            Organisation loaded = dao.findByBaseUrl("http://localhost:8080");
            assertNotNull(loaded);
            assertEquals("Tournois Test", loaded.getName());
            assertEquals("admin@example.org", loaded.getSuperUserEmail());
            assertEquals("pk_live", loaded.getStripePublic());
            assertEquals("cid", loaded.getOauthClientId());
            assertEquals("https://sso.example/auth", loaded.getOauthAuthorizationUrl());
            return org.getId();
        });

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps =
                        connection.prepareStatement("SELECT settings_json FROM organisation WHERE id = ?")) {
            ps.setInt(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                JSONObject raw = new JSONObject(rs.getString(1));
                assertEquals("Tournois Test", raw.getString("org.name"));
                assertEquals("pk_live", raw.getString("stripe.public"));
            }
        }
    }

    @Test
    void slimSchemaHasNoLegacyColumns() throws Exception {
        DataSource dataSource = TestDb.newDataSource();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                                + "WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'organisation'");
                ResultSet rs = ps.executeQuery()) {
            boolean sawName = false;
            boolean sawStripe = false;
            boolean sawSlug = false;
            boolean sawSettings = false;
            while (rs.next()) {
                String column = rs.getString(1).toLowerCase();
                if ("name".equals(column)) {
                    sawName = true;
                }
                if ("stripe_public".equals(column)) {
                    sawStripe = true;
                }
                if ("slug".equals(column)) {
                    sawSlug = true;
                }
                if ("settings_json".equals(column)) {
                    sawSettings = true;
                }
            }
            assertTrue(sawSettings);
            assertFalse(sawName);
            assertFalse(sawStripe);
            assertFalse(sawSlug);
        }
    }
}
