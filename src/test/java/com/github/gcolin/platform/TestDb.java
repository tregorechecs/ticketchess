package com.github.gcolin.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Creates a private in-memory H2 database initialised from {@code schema.sql}. */
public final class TestDb {

    private TestDb() {}

    /** A pooled-less data source over a fresh in-memory database (schema created). */
    public static javax.sql.DataSource newDataSource() {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try (Connection connection = ds.getConnection()) {
            for (String sql : schemaStatements()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create test database", e);
        }
        return ds;
    }

    public static Connection newConnection() {
        try {
            Connection connection = DriverManager.getConnection(
                    "jdbc:h2:mem:" + UUID.randomUUID()
                            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            for (String sql : schemaStatements()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(sql);
                }
            }
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create test database", e);
        }
    }

    static List<String> schemaStatements() {
        try (InputStream in = TestDb.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder cleaned = new StringBuilder();
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--")) {
                    continue;
                }
                cleaned.append(line).append('\n');
            }
            List<String> result = new ArrayList<>();
            for (String part : cleaned.toString().split(";")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
