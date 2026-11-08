package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parseEnumNamesFromCheckConstraintShouldReadInClauseValues() {
        Set<String> values = PersistenceService.parseEnumNamesFromCheckConstraint(
                "CHECK (option_type IN ('FFE_ID', 'OPTION_A', 'OPTION_B'))");

        assertEquals(Set.of("OPTION_B", "OPTION_A", "FFE_ID"), values);
    }

    @Test
    void parseEnumNamesFromCheckConstraintShouldReadPostgresArrayValues() {
        String constraintDef =
                "CHECK (((option_type)::text = ANY ((ARRAY['FFE_ID'::character varying, 'OPTION_A'::character varying])::text[])))";

        Set<String> values = PersistenceService.parseEnumNamesFromCheckConstraint(constraintDef);

        assertEquals(Set.of("OPTION_A", "FFE_ID"), values);
    }

    @Test
    void parseEnumNamesFromCheckConstraintShouldReturnEmptySetWhenMissing() {
        assertTrue(PersistenceService.parseEnumNamesFromCheckConstraint(null).isEmpty());
        assertTrue(PersistenceService.parseEnumNamesFromCheckConstraint("").isEmpty());
    }

    @Test
    void pureMigrationHelpersResolveDatesAndEnvironmentFallbacks() throws Exception {
        Method firstNonBlank = PersistenceService.class.getDeclaredMethod("firstNonBlank", String[].class);
        firstNonBlank.setAccessible(true);
        assertEquals("chosen", firstNonBlank.invoke(null, (Object) new String[] {"", "  ", "chosen"}));
        assertNull(firstNonBlank.invoke(null, (Object) new String[] {null, " "}));

        Method envOrProperty = PersistenceService.class.getDeclaredMethod("envOrProperty", String.class);
        envOrProperty.setAccessible(true);
        System.setProperty("persistence.test.value", "property");
        try {
            assertEquals("property", envOrProperty.invoke(null, "persistence.test.value"));
        } finally {
            System.clearProperty("persistence.test.value");
        }

        Method coalesce = PersistenceService.class.getDeclaredMethod("coalesceTimestamp", Timestamp.class, Timestamp.class);
        coalesce.setAccessible(true);
        LocalDateTime now = LocalDateTime.of(2026, 3, 4, 5, 6);
        assertEquals(now, coalesce.invoke(null, Timestamp.valueOf(now), Timestamp.valueOf(now.plusDays(1))));
        assertEquals(now, coalesce.invoke(null, null, Timestamp.valueOf(now)));
        assertNull(coalesce.invoke(null, null, null));

        Class<?> seasonDates = Class.forName("com.github.gcolin.platform.PersistenceService$SeasonDates");
        var constructor = seasonDates.getDeclaredConstructor(int.class, LocalDate.class, LocalDate.class);
        constructor.setAccessible(true);
        Object season = constructor.newInstance(8, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31));
        Method resolve = PersistenceService.class.getDeclaredMethod(
                "resolveSeasonId", List.class, LocalDateTime.class, int.class);
        resolve.setAccessible(true);
        assertEquals(8, resolve.invoke(null, List.of(season), LocalDateTime.of(2026, 4, 1, 0, 0), 0));
        assertEquals(8, resolve.invoke(null, List.of(season), LocalDateTime.of(2025, 12, 15, 0, 0), 2));
        assertNull(resolve.invoke(null, List.of(season), null, 2));
        assertNull(resolve.invoke(null, List.of(season), LocalDateTime.of(2027, 1, 1, 0, 0), 0));
    }

    @Test
    void h2InitialisationSchemaPatchesAndBackfills() throws Exception {
        Config config = new Config();
        Properties props = config.getProperties();
        props.setProperty("db.type", "h2");
        props.setProperty("db.name", "persistcover");
        props.setProperty("db.h2.path", tempDir.toString());
        props.setProperty("testmode", "true");
        props.setProperty("jwt.key", "01234567890123456789012345678901");

        PersistenceService service = new PersistenceService(config);
        Method initDatasource = privateMethod("initDatasource");
        initDatasource.invoke(service);
        try {
            Object dataSource = privateField(service, "dataSource");
            try (Connection connection = ((com.zaxxer.hikari.HikariDataSource) dataSource).getConnection();
                    var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE club_season (id INT PRIMARY KEY, start_date DATE, end_date DATE)");
                statement.execute(
                        "CREATE TABLE membership (id INT PRIMARY KEY, created_at TIMESTAMP, updated_at TIMESTAMP)");
                statement.execute(
                        "CREATE TABLE membership_option (id INT PRIMARY KEY, created_at TIMESTAMP, updated_at TIMESTAMP, option_type ENUM('OLD'))");
                statement.execute("CREATE TABLE payment (id BIGINT PRIMARY KEY, type ENUM('OLD'))");
                statement.execute(
                        "CREATE TABLE user_authorization (id INT PRIMARY KEY, email VARCHAR(255), permission VARCHAR(64), scope_type VARCHAR(20), scope_id INT)");
                statement.execute("CREATE TABLE eventoption (id INT PRIMARY KEY, option_type ENUM('OLD'))");
                statement.execute(
                        "CREATE TABLE eventcollectionoption (id INT PRIMARY KEY, option_type ENUM('OLD'))");
                statement.execute("CREATE TABLE membership_option_enum (id INT PRIMARY KEY)");
            }

            invoke(service, "applySchemaPatches");
            try (Connection connection = ((com.zaxxer.hikari.HikariDataSource) dataSource).getConnection();
                    var statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO club_season(id,start_date,end_date,is_current) VALUES (1,'2026-09-01','2027-08-31',TRUE)");
                statement.execute(
                        "INSERT INTO membership(id,created_at,updated_at,season_id) VALUES (1,'2026-08-15',NULL,NULL)");
                statement.execute(
                        "INSERT INTO membership_option(id,created_at,updated_at,season_id,option_type) VALUES (1,NULL,'2026-10-01',NULL,'COURSES')");
                statement.execute(
                        "INSERT INTO user_authorization VALUES (1,'a@b','EVENT_READ','EVENT',1),(2,'a@b','EVENT_READ','EVENT',1),(3,'a@b','UNKNOWN','EVENT',1)");
            }
            invoke(service, "backfillMembershipTimestamps");
            try (Connection connection = ((com.zaxxer.hikari.HikariDataSource) dataSource).getConnection()) {
                privateMethod("backfillMembershipSeasonIds", Connection.class).invoke(service, connection);
            }
            try (Connection connection = ((com.zaxxer.hikari.HikariDataSource) dataSource).getConnection();
                    var rs = connection.createStatement().executeQuery(
                            "SELECT m.season_id, m.updated_at, o.season_id, o.created_at FROM membership m CROSS JOIN membership_option o")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(1, rs.getInt(3));
                assertTrue(rs.getTimestamp(2) != null);
                assertTrue(rs.getTimestamp(4) != null);
            }
        } finally {
            invoke(service, "shutdown");
        }
    }

    @Test
    void persistenceInitAndRequireValidation() throws Exception {
        Config config = new Config();
        config.getProperties().putAll(
                java.util.Map.of("db.type", "h2", "db.name", "persistinit", "testmode", "true",
                        "jwt.key", "01234567890123456789012345678901"));
        PersistenceService service = new PersistenceService(config);
        try {
            service.init();
            assertTrue(((com.zaxxer.hikari.HikariDataSource) service.getDataSource()).isRunning());
        } finally {
            service.shutdown();
        }
        Method require = privateMethod("require", String.class, String.class);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> require.invoke(service, "", "required"));
        require.invoke(service, "ok", "required");
    }

    @Test
    void dropLegacyChessEventOptionsRemovesRowsAndRealignsEnum() throws Exception {
        Config config = new Config();
        Properties props = config.getProperties();
        props.setProperty("db.type", "h2");
        props.setProperty("db.name", "chessopt");
        props.setProperty("db.h2.path", tempDir.toString());
        props.setProperty("testmode", "true");
        props.setProperty("jwt.key", "01234567890123456789012345678901");

        PersistenceService service = new PersistenceService(config);
        privateMethod("initDatasource").invoke(service);
        try {
            var dataSource = (com.zaxxer.hikari.HikariDataSource) privateField(service, "dataSource");
            try (Connection connection = dataSource.getConnection();
                    var st = connection.createStatement()) {
                st.execute(
                        """
                        CREATE TABLE eventoption (
                            id INT PRIMARY KEY,
                            option_type ENUM(
                                'FFE_ID','FFE_PASSWORD','MAX_ELO','MIN_ELO','MAX_SUBSCRIPTIONS',
                                'ROUNDS','CADENCE','CLUB_REF','PAIRING','POINTAGE',
                                'CHESS_EVENT_USER','CHESS_EVENT_PASSWORD')
                        )
                        """);
                st.execute(
                        """
                        CREATE TABLE eventcollectionoption (
                            id INT PRIMARY KEY,
                            option_type ENUM('MAX_SUBSCRIPTIONS','CHESS_EVENT_ID')
                        )
                        """);
                st.execute("INSERT INTO eventoption(id, option_type) VALUES (1, 'FFE_ID'), (2, 'CHESS_EVENT_USER'), (3, 'CHESS_EVENT_PASSWORD')");
                st.execute(
                        "INSERT INTO eventcollectionoption(id, option_type) VALUES (1, 'MAX_SUBSCRIPTIONS'), (2, 'CHESS_EVENT_ID')");
            }

            invoke(service, "dropLegacyChessEventOptions");

            try (Connection connection = dataSource.getConnection();
                    var st = connection.createStatement()) {
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM eventoption")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                try (var rs = st.executeQuery("SELECT option_type FROM eventoption")) {
                    assertTrue(rs.next());
                    assertEquals("FFE_ID", rs.getString(1));
                }
                try (var rs = st.executeQuery("SELECT COUNT(*) FROM eventcollectionoption")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
                try (var rs = st.executeQuery("SELECT option_type FROM eventcollectionoption")) {
                    assertTrue(rs.next());
                    assertEquals("MAX_SUBSCRIPTIONS", rs.getString(1));
                }
            }
        } finally {
            invoke(service, "shutdown");
        }
    }

    @Test
    void foldOrganisationColumnsIntoSettingsJsonThenDropsLegacy() throws Exception {
        Config config = new Config();
        Properties props = config.getProperties();
        props.setProperty("db.type", "h2");
        props.setProperty("db.name", "orgfold");
        props.setProperty("db.h2.path", tempDir.toString());
        props.setProperty("testmode", "true");
        props.setProperty("jwt.key", "01234567890123456789012345678901");

        PersistenceService service = new PersistenceService(config);
        privateMethod("initDatasource").invoke(service);
        try {
            var dataSource = (com.zaxxer.hikari.HikariDataSource) privateField(service, "dataSource");
            try (Connection connection = dataSource.getConnection();
                    var st = connection.createStatement()) {
                st.execute(
                        """
                        CREATE TABLE organisation (
                            id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                            slug VARCHAR(100) NOT NULL,
                            base_url VARCHAR(500) NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            super_user_email VARCHAR(320) NOT NULL,
                            active BOOLEAN NOT NULL DEFAULT TRUE,
                            stripe_public VARCHAR(512),
                            stripe_secret VARCHAR(512),
                            oauth_client_id VARCHAR(512),
                            settings_json CLOB
                        )
                        """);
                st.execute(
                        """
                        INSERT INTO organisation(slug, base_url, name, super_user_email, active,
                            stripe_public, stripe_secret, oauth_client_id, settings_json)
                        VALUES ('legacy', 'http://localhost:8080', 'Legacy Club', 'admin@example.org', TRUE,
                            'pk_legacy', 'sk_legacy', 'oauth-legacy',
                            '{"feature.events":"true","org.name":"KeepJson"}')
                        """);
            }

            invoke(service, "foldOrganisationColumnsIntoSettingsJson");

            try (Connection connection = dataSource.getConnection();
                    var rs = connection.createStatement().executeQuery(
                            "SELECT settings_json FROM organisation WHERE base_url = 'http://localhost:8080'")) {
                assertTrue(rs.next());
                org.json.JSONObject json = new org.json.JSONObject(rs.getString(1));
                assertEquals("KeepJson", json.getString("org.name"));
                assertEquals("admin@example.org", json.getString("org.superUserEmail"));
                assertEquals("pk_legacy", json.getString("stripe.public"));
                assertEquals("sk_legacy", json.getString("stripe.secret"));
                assertEquals("oauth-legacy", json.getString("oauth.clientId"));
                assertEquals("true", json.getString("feature.events"));
            }

            invoke(service, "dropOrganisationSlugColumn");

            try (Connection connection = dataSource.getConnection();
                    var cols = connection
                            .createStatement()
                            .executeQuery(
                                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                                            + "WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = 'organisation'")) {
                java.util.Set<String> names = new java.util.HashSet<>();
                while (cols.next()) {
                    names.add(cols.getString(1).toLowerCase());
                }
                assertTrue(names.contains("settings_json"));
                assertTrue(names.contains("base_url"));
                assertTrue(names.contains("active"));
                assertFalse(names.contains("slug"));
                assertFalse(names.contains("name"));
                assertFalse(names.contains("stripe_public"));
                assertFalse(names.contains("oauth_client_id"));
                assertFalse(names.contains("super_user_email"));
            }

            invoke(service, "foldOrganisationColumnsIntoSettingsJson");
        } finally {
            invoke(service, "shutdown");
        }
    }

    private static Method privateMethod(String name, Class<?>... types) throws Exception {
        Method method = PersistenceService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static void invoke(PersistenceService service, String name) throws Exception {
        privateMethod(name).invoke(service);
    }

    private static Object privateField(PersistenceService service, String name) throws Exception {
        var field = PersistenceService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }
}
