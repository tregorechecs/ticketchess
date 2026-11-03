package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parseEnumNamesFromCheckConstraintShouldReadInClauseValues() {
        Set<String> values = PersistenceService.parseEnumNamesFromCheckConstraint(
                "CHECK (option_type IN ('FFE_ID', 'CHESS_EVENT_USER', 'CHESS_EVENT_PASSWORD'))");

        assertEquals(Set.of("CHESS_EVENT_PASSWORD", "CHESS_EVENT_USER", "FFE_ID"), values);
    }

    @Test
    void parseEnumNamesFromCheckConstraintShouldReadPostgresArrayValues() {
        String constraintDef =
                "CHECK (((option_type)::text = ANY ((ARRAY['FFE_ID'::character varying, 'CHESS_EVENT_USER'::character varying])::text[])))";

        Set<String> values = PersistenceService.parseEnumNamesFromCheckConstraint(constraintDef);

        assertEquals(Set.of("CHESS_EVENT_USER", "FFE_ID"), values);
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
            assertTrue(service.getEmf().isOpen());
        } finally {
            service.shutdown();
        }
        Method require = privateMethod("require", String.class, String.class);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> require.invoke(service, "", "required"));
        require.invoke(service, "ok", "required");
    }

    @Test
    void importsPostgresCopyDumpIntoH2AndSkipsUnknownColumns() throws Exception {
        Config config = new Config();
        config.getProperties().putAll(java.util.Map.of(
                "db.type", "h2",
                "db.name", "dumpcover",
                "testmode", "true",
                "jwt.key", "01234567890123456789012345678901"));
        PersistenceService service = new PersistenceService(config);
        privateMethod("initDatasource").invoke(service);
        Object dataSource = privateField(service, "dataSource");
        Path dump = tempDir.resolve("dump.sql");
        Files.writeString(dump, """
                -- ignored SQL
                COPY public.event (id, name, removed_column) FROM stdin;
                1	First	ignored
                2	Second	\\N
                \\. 
                COPY public.missing (id, name) FROM stdin;
                9	Unknown
                \\. 
                malformed COPY line
                """);
        try (Connection connection = ((com.zaxxer.hikari.HikariDataSource) dataSource).getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE event (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(100))");
            assertTrue((Boolean) privateMethod("isLikelyEmpty", Connection.class).invoke(service, connection));
            int inserted = (Integer) privateMethod("importPostgresCopySections", Connection.class, Path.class)
                    .invoke(service, connection, dump);
            assertEquals(2, inserted);
            try (var rows = statement.executeQuery("SELECT name FROM event ORDER BY id")) {
                assertTrue(rows.next());
                assertEquals("First", rows.getString(1));
                assertTrue(rows.next());
                assertEquals("Second", rows.getString(1));
            }
        } finally {
            service.shutdown();
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
