package com.github.gcolin.platform;

import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.event.EventCollectionOptionType;
import com.github.gcolin.event.EventOptionType;
import com.github.gcolin.membership.MembershipOptionType;
import com.github.gcolin.organisation.OrganisationSettings;
import com.github.gcolin.payment.PaymentType;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);
    private static final Pattern CHECK_CONSTRAINT_ENUM_VALUE =
            Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    private final Config config;
    private HikariDataSource dataSource;
    private final Map<String, Set<String>> tableColumnsCache = new HashMap<>();

    public PersistenceService(Config config) {
        this.config = config;
    }

    public void init() {
        initDatasource();
        initSchemaIfNeeded();
        //applySchemaPatches();
        migrateLegacyPermissionGrantsToRoles();
        dropLegacyChessEventOptions();
        renameOrganisationTransferColumns();
        dropOrganisationThemeColumn();
        migrateLegacyMoneyColumnsToCents();
        ensureOrgIdOnClubSeasonAndLicense();
        ensureOrgIdOnEventTables();
        foldEventInfoIntoEventDescription();
        foldOrganisationColumnsIntoSettingsJson();
        dropOrganisationSlugColumn();
        normalizeOrganisationSettingsAliases();
        //backfillMembershipSeasonIdsAfterBootstrap();
    }

    /** Maps legacy permission / EVENT_ADMIN grants to RoleCode values and realigns the role check. */
    private void migrateLegacyPermissionGrantsToRoles() {
        try (Connection connection = dataSource.getConnection()) {
            migratePermissionsToRoles(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate user_authorization roles", e);
        }
    }

    /** Creates the schema from {@code schema.sql} when the key tables are missing. */
    private void initSchemaIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            if (tableExists(connection, "event") && tableExists(connection, "organisation")) {
                return;
            }
            for (String statement : loadSchemaStatements()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(statement);
                }
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            tableColumnsCache.clear();
            logger.info("Database schema created from schema.sql");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create database schema", e);
        }
    }

    static List<String> loadSchemaStatements() {
        try (var in = PersistenceService.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return splitSqlStatements(script);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema.sql", e);
        }
    }

    /** Splits a SQL script on {@code ;} after dropping {@code --} comment lines. */
    static List<String> splitSqlStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String statement = part.trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private void renameOrganisationTransferColumns() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation")) {
                return;
            }
            renameOrganisationColumn(
                    connection, "stripe_transfer_events", "payment_transfer_events");
            renameOrganisationColumn(
                    connection, "stripe_transfer_memberships", "payment_transfer_memberships");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to rename organisation transfer columns", e);
        }
    }

    private void renameOrganisationColumn(Connection connection, String oldName, String newName) throws SQLException {
        boolean hasOld = columnExists(connection, "organisation", oldName);
        boolean hasNew = columnExists(connection, "organisation", newName);
        if (!hasOld || hasNew) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            if (isH2()) {
                st.execute("ALTER TABLE organisation ALTER COLUMN " + oldName + " RENAME TO " + newName);
            } else {
                st.execute("ALTER TABLE organisation RENAME COLUMN " + oldName + " TO " + newName);
            }
        }
        tableColumnsCache.remove("organisation");
        logger.info("Renamed organisation.{} to {}", oldName, newName);
    }

    private void dropOrganisationThemeColumn() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation") || !columnExists(connection, "organisation", "theme")) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE organisation DROP COLUMN theme");
                } else {
                    st.execute("ALTER TABLE organisation DROP COLUMN IF EXISTS theme");
                }
            }
            tableColumnsCache.remove("organisation");
            logger.info("Dropped obsolete organisation.theme column");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop organisation.theme column", e);
        }
    }

    /** Drops obsolete {@code organisation.slug}; tenants are resolved by {@code base_url} host only. */
    private void dropOrganisationSlugColumn() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation") || !columnExists(connection, "organisation", "slug")) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE organisation DROP COLUMN slug");
                } else {
                    st.execute("ALTER TABLE organisation DROP CONSTRAINT IF EXISTS uk_organisation_slug");
                    st.execute("ALTER TABLE organisation DROP COLUMN IF EXISTS slug");
                }
            }
            tableColumnsCache.remove("organisation");
            logger.info("Dropped obsolete organisation.slug column");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop organisation.slug column", e);
        }
    }

    /**
     * Moves legacy organisation typed columns into {@code settings_json}, then drops them.
     * Target shape: id / base_url / active / settings_json only.
     */
    private void foldOrganisationColumnsIntoSettingsJson() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation")) {
                return;
            }
            List<String> legacy = organisationLegacyColumns(connection);
            if (legacy.isEmpty()) {
                return;
            }
            if (!columnExists(connection, "organisation", "settings_json")) {
                try (Statement st = connection.createStatement()) {
                    if (isH2()) {
                        st.execute("ALTER TABLE organisation ADD COLUMN settings_json CLOB");
                    } else {
                        st.execute("ALTER TABLE organisation ADD COLUMN IF NOT EXISTS settings_json TEXT");
                    }
                }
                tableColumnsCache.remove("organisation");
            }
            foldOrganisationRows(connection, legacy);
            dropOrganisationLegacyColumns(connection, legacy);
            tableColumnsCache.remove("organisation");
            logger.info("Folded organisation columns into settings_json and dropped {}", legacy);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fold organisation columns into settings_json", e);
        }
    }

    private static final List<String> ORGANISATION_LEGACY_COLUMNS = List.of(
            "name",
            "super_user_email",
            "stripe_public",
            "stripe_secret",
            "stripe_keyprefix",
            "stripe_simuled",
            "stripe_card_events",
            "stripe_card_memberships",
            "payment_transfer_events",
            "payment_transfer_memberships",
            "oauth_client_id",
            "oauth_client_secret",
            "oauth_authorization_url",
            "oauth_token_url",
            "oauth_scope",
            "oauth_logout_url",
            "oauth_account_url",
            "oauth_userinfo_url",
            "mail_user_name",
            "mail_password");

    private List<String> organisationLegacyColumns(Connection connection) throws SQLException {
        List<String> present = new ArrayList<>();
        for (String column : ORGANISATION_LEGACY_COLUMNS) {
            if (columnExists(connection, "organisation", column)) {
                present.add(column);
            }
        }
        return present;
    }

    private void foldOrganisationRows(Connection connection, List<String> legacy) throws SQLException {
        String sql = "SELECT id, settings_json, " + String.join(", ", legacy) + " FROM organisation";
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql);
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE organisation SET settings_json = ? WHERE id = ?")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String settingsJson = rs.getString("settings_json");
                OrganisationSettings.LegacyColumns cols = new OrganisationSettings.LegacyColumns(
                        stringCol(rs, "name"),
                        stringCol(rs, "super_user_email"),
                        stringCol(rs, "stripe_public"),
                        stringCol(rs, "stripe_secret"),
                        stringCol(rs, "stripe_keyprefix"),
                        boolCol(rs, "stripe_simuled"),
                        boolCol(rs, "stripe_card_events"),
                        boolCol(rs, "stripe_card_memberships"),
                        boolCol(rs, "payment_transfer_events"),
                        boolCol(rs, "payment_transfer_memberships"),
                        stringCol(rs, "oauth_client_id"),
                        stringCol(rs, "oauth_client_secret"),
                        stringCol(rs, "oauth_authorization_url"),
                        stringCol(rs, "oauth_token_url"),
                        stringCol(rs, "oauth_scope"),
                        stringCol(rs, "oauth_logout_url"),
                        stringCol(rs, "oauth_account_url"),
                        stringCol(rs, "oauth_userinfo_url"),
                        stringCol(rs, "mail_user_name"),
                        stringCol(rs, "mail_password"));
                String merged = OrganisationSettings.mergeLegacyColumns(settingsJson, cols);
                update.setString(1, merged);
                update.setInt(2, id);
                update.executeUpdate();
            }
        }
    }

    private void dropOrganisationLegacyColumns(Connection connection, List<String> legacy) throws SQLException {
        try (Statement st = connection.createStatement()) {
            for (String column : legacy) {
                if (isH2()) {
                    st.execute("ALTER TABLE organisation DROP COLUMN " + column);
                } else {
                    st.execute("ALTER TABLE organisation DROP COLUMN IF EXISTS " + column);
                }
            }
        }
    }

    private static String stringCol(ResultSet rs, String column) throws SQLException {
        try {
            String value = rs.getString(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            return null;
        }
    }

    private static Boolean boolCol(ResultSet rs, String column) throws SQLException {
        try {
            boolean value = rs.getBoolean(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Folds {@code keycloak.*} → {@code oauth.*}, {@code org.hosting.address} → {@code org.address},
     * and drops invoice seller keys that duplicate org fields.
     */
    private void normalizeOrganisationSettingsAliases() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation")
                    || !columnExists(connection, "organisation", "settings_json")) {
                return;
            }
            int updated = 0;
            try (Statement st = connection.createStatement();
                    ResultSet rs = st.executeQuery("SELECT id, settings_json FROM organisation");
                    PreparedStatement update = connection.prepareStatement(
                            "UPDATE organisation SET settings_json = ? WHERE id = ?")) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String before = rs.getString("settings_json");
                    String after = OrganisationSettings.normalizeJson(before);
                    if (OrganisationSettings.jsonEquivalent(before, after)) {
                        continue;
                    }
                    update.setString(1, after);
                    update.setInt(2, id);
                    update.executeUpdate();
                    updated++;
                }
            }
            if (updated > 0) {
                logger.info("Normalized organisation settings aliases on {} row(s)", updated);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to normalize organisation settings aliases", e);
        }
    }

    /**
     * Legacy DBs created before multi-org owned {@code club_season}/{@code license} rows without
     * {@code org_id}. Backfill from the first organisation and realign the license unique key.
     */
    private void ensureOrgIdOnClubSeasonAndLicense() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation")) {
                return;
            }
            boolean changed = false;
            changed |= ensureOrgIdColumn(connection, "club_season");
            changed |= ensureOrgIdColumn(connection, "license");
            if (tableExists(connection, "license") && columnExists(connection, "license", "org_id")) {
                changed |= ensureLicenseOrgNameUnique(connection);
            }
            if (changed) {
                logger.info("Ensured org_id on club_season and license");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure org_id on club_season and license", e);
        }
    }

    /** Ensures {@code org_id} on {@code event}, {@code event_group} and {@code big_event}. */
    private void ensureOrgIdOnEventTables() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "organisation")) {
                return;
            }
            boolean changed = false;
            changed |= ensureOrgIdColumn(connection, "event");
            changed |= ensureOrgIdColumn(connection, "event_group");
            changed |= ensureOrgIdColumn(connection, "big_event");
            if (changed) {
                logger.info("Ensured org_id on event, event_group and big_event");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure org_id on event tables", e);
        }
    }

    /** Moves legacy {@code eventinfo.description} onto {@code event.description}, then drops {@code eventinfo}. */
    private void foldEventInfoIntoEventDescription() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "event")) {
                return;
            }
            if (!columnExists(connection, "event", "description")) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE event ADD COLUMN description TEXT");
                }
                tableColumnsCache.remove("event");
                logger.info("Added event.description column");
            }
            if (!tableExists(connection, "eventinfo")) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                int updated;
                if (isH2()) {
                    updated = st.executeUpdate(
                            "UPDATE event SET description = ("
                                    + "SELECT description FROM eventinfo WHERE eventinfo.eventid = event.id)"
                                    + " WHERE (description IS NULL OR description = '')"
                                    + " AND EXISTS (SELECT 1 FROM eventinfo WHERE eventinfo.eventid = event.id"
                                    + " AND eventinfo.description IS NOT NULL)");
                } else {
                    updated = st.executeUpdate(
                            "UPDATE event e SET description = ei.description FROM eventinfo ei"
                                    + " WHERE e.id = ei.eventid"
                                    + " AND (e.description IS NULL OR e.description = '')"
                                    + " AND ei.description IS NOT NULL");
                }
                if (updated > 0) {
                    logger.info("Copied {} eventinfo description(s) into event.description", updated);
                }
                st.execute("DROP TABLE eventinfo");
            }
            tableColumnsCache.remove("eventinfo");
            logger.info("Dropped legacy eventinfo table");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fold eventinfo into event.description", e);
        }
    }

    /**
     * Adds {@code org_id} when missing, backfills nulls from the first organisation, then sets NOT NULL.
     * Safe to call before and after {@link com.github.gcolin.organisation.OrganisationBootstrap}: if no
     * organisation row exists yet, the column stays nullable until a later pass.
     */
    private boolean ensureOrgIdColumn(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            return false;
        }
        boolean changed = false;
        if (!columnExists(connection, table, "org_id")) {
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN org_id INTEGER");
                } else {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS org_id INTEGER");
                }
            }
            tableColumnsCache.remove(table.toLowerCase(Locale.ROOT));
            changed = true;
        }
        Integer defaultOrgId = minOrganisationId(connection);
        if (defaultOrgId == null) {
            return changed;
        }
        try (Statement st = connection.createStatement()) {
            int updated = st.executeUpdate(
                    "UPDATE " + table + " SET org_id = " + defaultOrgId + " WHERE org_id IS NULL");
            if (updated > 0) {
                changed = true;
            }
            if (isColumnNullable(connection, table, "org_id")) {
                st.execute("ALTER TABLE " + table + " ALTER COLUMN org_id SET NOT NULL");
                changed = true;
            }
        }
        return changed;
    }

    /** Re-run org_id backfill after {@code OrganisationBootstrap} has created the default org. */
    public void ensureOrgIdsAfterOrganisationBootstrap() {
        ensureOrgIdOnClubSeasonAndLicense();
        ensureOrgIdOnEventTables();
    }

    private Integer minOrganisationId(Connection connection) throws SQLException {
        if (!tableExists(connection, "organisation")) {
            return null;
        }
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT MIN(id) FROM organisation")) {
            if (!rs.next()) {
                return null;
            }
            int id = rs.getInt(1);
            return rs.wasNull() ? null : id;
        }
    }

    private boolean isColumnNullable(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND LOWER(COLUMN_NAME) = ?")) {
            ps.setString(1, informationSchemaName());
            ps.setString(2, informationSchemaTableName(table));
            ps.setString(3, column.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return "YES".equalsIgnoreCase(rs.getString(1));
            }
        }
    }

    private boolean ensureLicenseOrgNameUnique(Connection connection) throws SQLException {
        dropLegacyLicenseNameUnique(connection);
        if (constraintExists(connection, "license", "uk_license_org_name")) {
            return false;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE license ADD CONSTRAINT uk_license_org_name UNIQUE (org_id, name)");
        }
        return true;
    }

    private void dropLegacyLicenseNameUnique(Connection connection) throws SQLException {
        List<String> singleColumnNameUniques = findUniqueConstraintsOnSingleColumn(connection, "license", "name");
        // Known Hibernate-generated name seen on Postgres integration DBs.
        if (!isH2() && singleColumnNameUniques.stream().noneMatch(n -> n.equalsIgnoreCase("uk888jmhmieccsusvllotgvnrtm"))) {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE license DROP CONSTRAINT IF EXISTS uk888jmhmieccsusvllotgvnrtm");
            }
        }
        for (String constraintName : singleColumnNameUniques) {
            if ("uk_license_org_name".equalsIgnoreCase(constraintName)) {
                continue;
            }
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE license DROP CONSTRAINT " + constraintName);
                } else {
                    st.execute("ALTER TABLE license DROP CONSTRAINT IF EXISTS " + constraintName);
                }
            }
        }
    }

    private boolean constraintExists(Connection connection, String table, String constraintName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?")) {
            ps.setString(1, informationSchemaName());
            ps.setString(2, informationSchemaTableName(table));
            ps.setString(3, constraintName.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private List<String> findUniqueConstraintsOnSingleColumn(Connection connection, String table, String column)
            throws SQLException {
        String sql =
                "SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
                        + "ON tc.CONSTRAINT_SCHEMA = kcu.CONSTRAINT_SCHEMA "
                        + "AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME "
                        + "AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA "
                        + "AND tc.TABLE_NAME = kcu.TABLE_NAME "
                        + "WHERE tc.TABLE_SCHEMA = ? AND tc.TABLE_NAME = ? "
                        + "AND tc.CONSTRAINT_TYPE = 'UNIQUE' "
                        + "GROUP BY tc.CONSTRAINT_NAME "
                        + "HAVING COUNT(*) = 1 AND MIN(kcu.COLUMN_NAME) = ?";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, informationSchemaName());
            ps.setString(2, informationSchemaTableName(table));
            ps.setString(3, column.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }

    /**
     * Migrates legacy euro float/numeric money columns to integer cents, then drops them.
     * Idempotent: no-op when {@code event.price}, {@code event.youngprice} and {@code payment.amount}
     * are already gone.
     */
    private void migrateLegacyMoneyColumnsToCents() {
        try (Connection connection = dataSource.getConnection()) {
            boolean hasEventPrice =
                    tableExists(connection, "event") && columnExists(connection, "event", "price");
            boolean hasEventYoungPrice =
                    tableExists(connection, "event") && columnExists(connection, "event", "youngprice");
            boolean hasPaymentAmount =
                    tableExists(connection, "payment") && columnExists(connection, "payment", "amount");
            if (!hasEventPrice && !hasEventYoungPrice && !hasPaymentAmount) {
                return;
            }

            try (Statement st = connection.createStatement()) {
                if (tableExists(connection, "event")) {
                    ensureBigintColumn(connection, st, "event", "pricecents");
                    ensureBigintColumn(connection, st, "event", "youngpricecents");
                    if (hasEventPrice) {
                        st.execute(
                                "UPDATE event SET pricecents = ROUND(price * 100)"
                                        + " WHERE pricecents IS NULL AND price IS NOT NULL");
                    }
                    if (hasEventYoungPrice) {
                        st.execute(
                                "UPDATE event SET youngpricecents = ROUND(youngprice * 100)"
                                        + " WHERE youngpricecents IS NULL AND youngprice IS NOT NULL");
                    }
                    if (hasEventPrice) {
                        dropColumn(st, "event", "price");
                    }
                    if (hasEventYoungPrice) {
                        dropColumn(st, "event", "youngprice");
                    }
                    tableColumnsCache.remove("event");
                }
                if (tableExists(connection, "payment")) {
                    ensureBigintColumn(connection, st, "payment", "amountcents");
                    if (hasPaymentAmount) {
                        st.execute(
                                "UPDATE payment SET amountcents = ROUND(amount * 100)"
                                        + " WHERE amountcents IS NULL AND amount IS NOT NULL");
                        dropColumn(st, "payment", "amount");
                    }
                    tableColumnsCache.remove("payment");
                }
            }
            logger.info("Migrated legacy money columns to cents and dropped float/numeric columns");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate legacy money columns to cents", e);
        }
    }

    private void ensureBigintColumn(Connection connection, Statement st, String table, String column)
            throws SQLException {
        if (columnExists(connection, table, column)) {
            return;
        }
        if (isH2()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " BIGINT");
        } else {
            st.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " BIGINT");
        }
        tableColumnsCache.remove(table);
    }

    private void dropColumn(Statement st, String table, String column) throws SQLException {
        if (isH2()) {
            st.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        } else {
            st.execute("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column);
        }
    }

    private void backfillMembershipSeasonIdsAfterBootstrap() {
        try (Connection connection = dataSource.getConnection()) {
            backfillMembershipSeasonIds(connection);
        } catch (SQLException e) {
            logger.warn("Failed to backfill membership season ids after bootstrap", e);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void shutdown() {
        if (dataSource != null) dataSource.close();
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void initDatasource() {
        Properties properties = config.getProperties();
        String defaultDatabaseName = "ticketckess";
        String dbHost = properties.getProperty("db.host");
        String dbName = properties.getProperty("db.name", defaultDatabaseName);
        String dbUser = properties.getProperty("db.user");
        String dbPass = properties.getProperty("db.pass");
        String dbType = properties.getProperty("db.type", "h2");

        dataSource = new HikariDataSource();

        if ("postgres".equals(dbType)) {
            require(dbHost, "DB_HOST is required for postgres");
            require(dbUser, "DB_USER is required for postgres");
            require(dbPass, "DB_PASS is required for postgres");

            String databaseUrl = "jdbc:postgresql://" + dbHost
                    + "/"
                    + dbName
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
            dataSource.setDriverClassName("org.postgresql.Driver");
            logger.info("PostgreSQL using file-based database with authentication from environment variables");
            dataSource.setJdbcUrl(databaseUrl);
            dataSource.setUsername(dbUser);
            dataSource.setPassword(dbPass);
            dataSource.setMinimumIdle(0);
            dataSource.setMaximumPoolSize(4);
        } else {
            boolean h2Persistent = Boolean.parseBoolean(envOrProperty("DB_H2_PERSISTENT"))
                    || Boolean.parseBoolean(properties.getProperty("db.h2.persistent", "false"));
            String h2PathEnv = firstNonBlank(envOrProperty("DB_H2_PATH"), properties.getProperty("db.h2.path"));
            String databaseUrl;

            if (h2Persistent) {
                String configDir = System.getenv("CONFIG_DIR");
                String baseDir = (h2PathEnv != null && !h2PathEnv.isBlank())
                        ? h2PathEnv
                        : (configDir == null || configDir.isBlank() ? "./h2" : configDir + "/h2");

                Path dbDir = Paths.get(baseDir);
                try {
                    Files.createDirectories(dbDir);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create H2 directory " + dbDir, e);
                }

                Path dbFile = dbDir.resolve(dbName);
                String dbFilePath = dbFile.toAbsolutePath().toString().replace('\\', '/');
                databaseUrl = "jdbc:h2:file:" + dbFilePath + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
                logger.info("h2 using persistent database on disk at {}", dbFilePath);
            } else {
                databaseUrl = "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
                logger.info(
                        "h2 using in-memory database (set db.h2.persistent=true or DB_H2_PERSISTENT=true for disk persistence)");
            }

            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setJdbcUrl(databaseUrl);
            dataSource.setMinimumIdle(1);
            dataSource.setMaximumPoolSize(2);
        }

        dataSource.setLeakDetectionThreshold(0);
    }

    private static String envOrProperty(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return System.getProperty(key);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Removes obsolete SharlyChess option rows ({@code CHESS_EVENT_*}) then realigns option_type
     * constraints with the current enums.
     */
    private void dropLegacyChessEventOptions() {
        try (Connection connection = dataSource.getConnection()) {
            int eventOptionsDeleted = 0;
            int collectionOptionsDeleted = 0;
            if (tableExists(connection, "eventoption") && columnExists(connection, "eventoption", "option_type")) {
                try (Statement st = connection.createStatement()) {
                    eventOptionsDeleted = st.executeUpdate(
                            "DELETE FROM eventoption WHERE option_type IN ('CHESS_EVENT_USER', 'CHESS_EVENT_PASSWORD')");
                }
            }
            if (tableExists(connection, "eventcollectionoption")
                    && columnExists(connection, "eventcollectionoption", "option_type")) {
                try (Statement st = connection.createStatement()) {
                    collectionOptionsDeleted =
                            st.executeUpdate("DELETE FROM eventcollectionoption WHERE option_type = 'CHESS_EVENT_ID'");
                }
            }
            if (eventOptionsDeleted > 0 || collectionOptionsDeleted > 0) {
                logger.info(
                        "Dropped legacy ChessEvent options (eventoption={}, eventcollectionoption={})",
                        eventOptionsDeleted,
                        collectionOptionsDeleted);
            }
            if (isH2()) {
                applyH2EnumColumnPatch(connection, "eventoption", "option_type", EventOptionType.class, true);
                applyH2EnumColumnPatch(
                        connection, "eventcollectionoption", "option_type", EventCollectionOptionType.class, true);
            } else {
                applyPostgresEnumCheckConstraintPatch(connection, "eventoption", "option_type", EventOptionType.class);
                applyPostgresEnumCheckConstraintPatch(
                        connection, "eventcollectionoption", "option_type", EventCollectionOptionType.class);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop legacy ChessEvent options", e);
        }
    }

    private void applySchemaPatches() {
        try (Connection connection = dataSource.getConnection()) {
            applyClubSeasonCurrentColumn(connection);
            applyMembershipLicenseTypeColumn(connection);
            applyMembershipSeasonColumns(connection);
            applyMembershipPaymentColumn(connection);
            applyPaymentDonationColumns(connection);
            migratePermissionsToRoles(connection);
            if (isH2()) {
                applyH2EnumColumnPatch(connection, "eventoption", "option_type", EventOptionType.class, true);
                applyH2EnumColumnPatch(
                        connection, "eventcollectionoption", "option_type", EventCollectionOptionType.class, true);
                applyH2EnumColumnPatch(
                        connection, "membership_option", "option_type", MembershipOptionType.class, true);
                applyH2EnumColumnPatch(connection, "payment", "type", PaymentType.class, false);
            } else {
                applyPostgresEnumCheckConstraintPatch(connection, "eventoption", "option_type", EventOptionType.class);
                applyPostgresEnumCheckConstraintPatch(
                        connection, "eventcollectionoption", "option_type", EventCollectionOptionType.class);
                applyPostgresEnumCheckConstraintPatch(
                        connection, "membership_option", "option_type", MembershipOptionType.class);
                applyPostgresEnumCheckConstraintPatch(connection, "payment", "type", PaymentType.class);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply schema patches", e);
        }
    }

    private boolean isH2() {
        return "h2".equalsIgnoreCase(config.getProperties().getProperty("db.type", "h2"));
    }

    private void applyH2EnumColumnPatch(
            Connection connection,
            String table,
            String column,
            Class<? extends Enum<?>> enumClass,
            boolean notNull)
            throws SQLException {
        String tableKey = table.toLowerCase(Locale.ROOT);
        String columnKey = column.toLowerCase(Locale.ROOT);
        if (!tableExists(connection, tableKey) || !columnExists(connection, tableKey, columnKey)) {
            return;
        }
        if (!isH2EnumColumn(connection, tableKey, columnKey)) {
            return;
        }

        String enumValues = Arrays.stream(enumClass.getEnumConstants())
                .map(value -> "'" + value.name() + "'")
                .collect(Collectors.joining(", "));
        String sql = "ALTER TABLE " + table + " ALTER COLUMN " + column + " ENUM(" + enumValues + ")"
                + (notNull ? " NOT NULL" : "");
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
        tableColumnsCache.remove(tableKey);
        logger.info("Updated {}.{} enum to match {}", table, column, enumClass.getSimpleName());
    }

    private void applyPostgresEnumCheckConstraintPatch(
            Connection connection, String table, String column, Class<? extends Enum<?>> enumClass) throws SQLException {
        if (!postgresTableHasColumn(connection, table, column)) {
            return;
        }

        String constraintName = table + "_" + column + "_check";
        Set<String> expectedValues = enumValues(enumClass);
        Set<String> currentValues = readPostgresCheckConstraintEnumValues(connection, constraintName);
        if (currentValues.equals(expectedValues)) {
            logger.debug("{}.{} check constraint already up to date", table, column);
            return;
        }

        String enumValues = expectedValues.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));

        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraintName);
            st.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + constraintName + " CHECK (" + column + " IN ("
                    + enumValues
                    + "))");
        }
        logger.info("Updated {}.{} check constraint to match {}", table, column, enumClass.getSimpleName());
    }

    private Set<String> readPostgresCheckConstraintEnumValues(Connection connection, String constraintName)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?")) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Set.of();
                }
                return parseEnumNamesFromCheckConstraint(rs.getString(1));
            }
        }
    }

    static Set<String> parseEnumNamesFromCheckConstraint(String constraintDef) {
        if (constraintDef == null || constraintDef.isBlank()) {
            return Set.of();
        }
        Set<String> values = new TreeSet<>();
        Matcher matcher = CHECK_CONSTRAINT_ENUM_VALUE.matcher(constraintDef);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> enumValues(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private boolean postgresTableHasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table.toLowerCase(Locale.ROOT));
            ps.setString(2, column.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean isH2EnumColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = 'public' AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            ps.setString(1, table.toLowerCase(Locale.ROOT));
            ps.setString(2, column.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return "ENUM".equalsIgnoreCase(rs.getString(1));
            }
        }
    }

    private void backfillMembershipTimestamps() {
        try (Connection connection = dataSource.getConnection()) {
            backfillTimedEntityColumns(connection, "membership");
            backfillTimedEntityColumns(connection, "membership_option");
        } catch (SQLException e) {
            logger.warn("Failed to backfill membership timestamps", e);
        }
    }

    private void backfillTimedEntityColumns(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)
                || !columnExists(connection, table, "created_at")
                || !columnExists(connection, table, "updated_at")) {
            return;
        }
        String sql = "UPDATE " + table.toLowerCase()
                + " SET created_at = COALESCE(created_at, updated_at, CURRENT_TIMESTAMP),"
                + " updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)"
                + " WHERE created_at IS NULL OR updated_at IS NULL";
        try (Statement st = connection.createStatement()) {
            int updated = st.executeUpdate(sql);
            if (updated > 0) {
                logger.info("Backfilled timestamps on {} {} row(s)", updated, table.toLowerCase());
            }
        }
    }

    private void applyClubSeasonCurrentColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, "club_season") || columnExists(connection, "club_season", "is_current")) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute(
                    "ALTER TABLE club_season ADD COLUMN IF NOT EXISTS is_current boolean NOT NULL DEFAULT false");
        }
        tableColumnsCache.remove("club_season");
        logger.info("Added club_season.is_current column with default false for existing rows");
    }

    private void applyMembershipLicenseTypeColumn(Connection connection) throws SQLException {
        if (isH2()) {
            if (!tableExists(connection, "membership") || columnExists(connection, "membership", "license_type")) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE membership ADD COLUMN license_type VARCHAR(10)");
            }
            tableColumnsCache.remove("membership");
        } else {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE membership ADD COLUMN IF NOT EXISTS license_type VARCHAR(10)");
            }
        }
        logger.info("Ensured membership.license_type column exists");
    }

    private void applyMembershipSeasonColumns(Connection connection) throws SQLException {
        if (isH2()) {
            if (tableExists(connection, "membership") && !columnExists(connection, "membership", "season_id")) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE membership ADD COLUMN season_id INTEGER");
                }
                tableColumnsCache.remove("membership");
            }
            if (tableExists(connection, "membership_option") && !columnExists(connection, "membership_option", "season_id")) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE membership_option ADD COLUMN season_id INTEGER");
                }
                tableColumnsCache.remove("membership_option");
            }
        } else {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE membership ADD COLUMN IF NOT EXISTS season_id INTEGER");
                st.execute("ALTER TABLE membership_option ADD COLUMN IF NOT EXISTS season_id INTEGER");
            }
        }
        backfillMembershipSeasonIds(connection);
        logger.info("Ensured membership and membership_option season_id columns exist");
    }

    private void applyMembershipPaymentColumn(Connection connection) throws SQLException {
        if (isH2()) {
            if (!tableExists(connection, "membership") || columnExists(connection, "membership", "payment_id")) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE membership ADD COLUMN payment_id BIGINT");
            }
            tableColumnsCache.remove("membership");
        } else {
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE membership ADD COLUMN IF NOT EXISTS payment_id BIGINT");
            }
        }
        logger.info("Ensured membership.payment_id column exists");
    }

    private void applyPaymentDonationColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, "payment")) {
            return;
        }
        boolean added = false;
        if (!columnExists(connection, "payment", "donation")) {
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE payment ADD COLUMN donation BOOLEAN DEFAULT FALSE NOT NULL");
                } else {
                    st.execute(
                            "ALTER TABLE payment ADD COLUMN IF NOT EXISTS donation boolean NOT NULL DEFAULT false");
                }
            }
            added = true;
        }
        if (!columnExists(connection, "payment", "payeraddress")) {
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE payment ADD COLUMN payerAddress VARCHAR(1000)");
                } else {
                    st.execute(
                            "ALTER TABLE payment ADD COLUMN IF NOT EXISTS payeraddress VARCHAR(1000)");
                }
            }
            added = true;
        }
        if (!columnExists(connection, "payment", "payername")) {
            try (Statement st = connection.createStatement()) {
                if (isH2()) {
                    st.execute("ALTER TABLE payment ADD COLUMN payerName VARCHAR(255)");
                } else {
                    st.execute("ALTER TABLE payment ADD COLUMN IF NOT EXISTS payername VARCHAR(255)");
                }
            }
            added = true;
        }
        if (added) {
            tableColumnsCache.remove("payment");
            logger.info("Ensured payment.donation, payment.payerAddress and payment.payerName columns exist");
        }
    }

    private static final int MEMBERSHIP_PRE_SEASON_MONTHS = 5;

    private void backfillMembershipSeasonIds(Connection connection) throws SQLException {
        if (!tableExists(connection, "club_season")) {
            return;
        }
        List<SeasonDates> seasons = loadClubSeasons(connection);
        if (seasons.isEmpty()) {
            return;
        }
        Integer fallbackSeasonId = resolveCurrentSeasonId(connection, seasons);
        if (tableExists(connection, "membership") && columnExists(connection, "membership", "season_id")) {
            int updated = backfillTableSeasonId(connection, "membership", seasons, MEMBERSHIP_PRE_SEASON_MONTHS, fallbackSeasonId);
            if (updated > 0) {
                logger.info("Backfilled season_id on {} membership row(s)", updated);
            }
        }
        if (tableExists(connection, "membership_option") && columnExists(connection, "membership_option", "season_id")) {
            int updated = backfillTableSeasonId(connection, "membership_option", seasons, 0, fallbackSeasonId);
            if (updated > 0) {
                logger.info("Backfilled season_id on {} membership_option row(s)", updated);
            }
        }
    }

    private Integer resolveCurrentSeasonId(Connection connection, List<SeasonDates> seasons) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM club_season WHERE is_current = true ORDER BY start_date DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return seasons.isEmpty() ? null : seasons.get(0).id();
    }

    private List<SeasonDates> loadClubSeasons(Connection connection) throws SQLException {
        List<SeasonDates> seasons = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, start_date, end_date FROM club_season ORDER BY start_date DESC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LocalDate startDate = rs.getDate("start_date").toLocalDate();
                LocalDate endDate = rs.getDate("end_date").toLocalDate();
                seasons.add(new SeasonDates(rs.getInt("id"), startDate, endDate));
            }
        }
        return seasons;
    }

    private int backfillTableSeasonId(
            Connection connection,
            String table,
            List<SeasonDates> seasons,
            int preSeasonMonths,
            Integer fallbackSeasonId)
            throws SQLException {
        int updated = 0;
        // Only fill missing season_id — never overwrite an existing assignment on startup.
        String selectSql = "SELECT id, created_at, updated_at FROM " + table + " WHERE season_id IS NULL";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
                ResultSet rows = select.executeQuery();
                PreparedStatement update =
                        connection.prepareStatement("UPDATE " + table + " SET season_id = ? WHERE id = ? AND season_id IS NULL")) {
            while (rows.next()) {
                LocalDateTime timestamp = coalesceTimestamp(rows.getTimestamp("created_at"), rows.getTimestamp("updated_at"));
                Integer seasonId = resolveSeasonId(seasons, timestamp, preSeasonMonths);
                if (seasonId == null) {
                    seasonId = fallbackSeasonId;
                }
                if (seasonId == null) {
                    continue;
                }
                update.setInt(1, seasonId);
                update.setInt(2, rows.getInt("id"));
                updated += update.executeUpdate();
            }
        }
        return updated;
    }

    private static LocalDateTime coalesceTimestamp(Timestamp createdAt, Timestamp updatedAt) {
        Timestamp value = createdAt != null ? createdAt : updatedAt;
        return value == null ? null : value.toLocalDateTime();
    }

    private static Integer resolveSeasonId(List<SeasonDates> seasons, LocalDateTime timestamp, int preSeasonMonths) {
        if (timestamp == null) {
            return null;
        }
        LocalDate date = timestamp.toLocalDate();
        for (SeasonDates season : seasons) {
            if (!date.isBefore(season.startDate()) && !date.isAfter(season.endDate())) {
                return season.id();
            }
        }
        if (preSeasonMonths > 0) {
            for (SeasonDates season : seasons) {
                LocalDate windowStart = season.startDate().minusMonths(preSeasonMonths);
                if (!date.isBefore(windowStart) && date.isBefore(season.startDate())) {
                    return season.id();
                }
            }
        }
        return null;
    }

    private record SeasonDates(int id, LocalDate startDate, LocalDate endDate) {}

    private void migratePermissionsToRoles(Connection connection) throws SQLException {
        if (!tableExists(connection, "user_authorization")) {
            return;
        }

        prepareUserAuthorizationRoleColumnForMigration(connection);

        boolean hasPermissionColumn = columnExists(connection, "user_authorization", "permission");
        boolean hasRoleColumn = columnExists(connection, "user_authorization", "role");
        String valueColumn = hasPermissionColumn ? "permission" : (hasRoleColumn ? "role" : null);
        if (valueColumn == null) {
            return;
        }

        Map<String, String> mapping = Map.ofEntries(
                Map.entry("USER_IMPERSONATE", "ADMIN"),
                Map.entry("ADMIN_PANEL", "ADMIN"),
                Map.entry("ADMIN_USER", "ADMIN"),
                Map.entry("PAYMENT_READ", "TRESORIER"),
                Map.entry("PAYMENT_WRITE", "TRESORIER"),
                Map.entry("EVENT_CREATE", "ARBITRE"),
                Map.entry("EVENT_EDIT", "ARBITRE"),
                Map.entry("EVENT_DELETE", "ARBITRE"),
                Map.entry("MAIL_SEND", "ARBITRE"),
                Map.entry("EVENT_READ", "ARBITRE"),
                Map.entry("EVENT_ADMIN", "ARBITRE"));

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String sql = "UPDATE user_authorization SET " + valueColumn + " = ? WHERE " + valueColumn + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, entry.getValue());
                ps.setString(2, entry.getKey());
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    logger.info(
                            "Migrated {} user_authorization grant(s) from {} to role {}",
                            updated,
                            entry.getKey(),
                            entry.getValue());
                }
            }
        }

        Set<String> validRoles = enumValues(RoleCode.class);
        String placeholders = String.join(", ", Collections.nCopies(validRoles.size(), "?"));
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM user_authorization WHERE " + valueColumn + " NOT IN (" + placeholders + ")")) {
            int index = 1;
            for (String role : validRoles) {
                ps.setString(index++, role);
            }
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("Removed {} user_authorization grant(s) with unrecognized values", deleted);
            }
        }

        deduplicateUserAuthorizations(connection, valueColumn);

        if (hasPermissionColumn && !hasRoleColumn) {
            if (isH2()) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE user_authorization ALTER COLUMN permission RENAME TO role");
                }
            } else {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE user_authorization RENAME COLUMN permission TO role");
                }
            }
            logger.info("Renamed user_authorization.permission column to role");
        }

        finalizeUserAuthorizationRoleColumn(connection);
    }

    private void prepareUserAuthorizationRoleColumnForMigration(Connection connection) throws SQLException {
        if (isH2()) {
            for (String column : List.of("permission", "role")) {
                if (!columnExists(connection, "user_authorization", column)) {
                    continue;
                }
                if (!isH2EnumColumn(connection, "user_authorization", column)) {
                    continue;
                }
                String sqlColumn = column.toLowerCase(Locale.ROOT);
                try (Statement st = connection.createStatement()) {
                    st.execute(
                            "ALTER TABLE user_authorization ALTER COLUMN " + sqlColumn + " VARCHAR(64) NOT NULL");
                }
                tableColumnsCache.remove("user_authorization");
                logger.info("Converted user_authorization.{} from legacy ENUM to VARCHAR(64)", sqlColumn);
                return;
            }
            return;
        }

        for (String column : List.of("permission", "role")) {
            if (!postgresTableHasColumn(connection, "user_authorization", column)) {
                continue;
            }
            String constraintName = "user_authorization_" + column + "_check";
            Set<String> currentValues = readPostgresCheckConstraintEnumValues(connection, constraintName);
            if (currentValues.isEmpty()) {
                continue;
            }
            Set<String> expectedValues = enumValues(RoleCode.class);
            if (currentValues.equals(expectedValues)) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE user_authorization DROP CONSTRAINT IF EXISTS " + constraintName);
            }
            logger.info("Dropped legacy {} check constraint before role migration", constraintName);
            return;
        }
    }

    private void finalizeUserAuthorizationRoleColumn(Connection connection) throws SQLException {
        if (!columnExists(connection, "user_authorization", "role") || isH2()) {
            return;
        }
        if (!postgresTableHasColumn(connection, "user_authorization", "role")) {
            return;
        }
        String constraintName = "user_authorization_role_check";
        Set<String> expectedValues = enumValues(RoleCode.class);
        Set<String> currentValues = readPostgresCheckConstraintEnumValues(connection, constraintName);
        if (currentValues.equals(expectedValues)) {
            return;
        }
        String enumValues = expectedValues.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE user_authorization DROP CONSTRAINT IF EXISTS " + constraintName);
            st.execute("ALTER TABLE user_authorization ADD CONSTRAINT " + constraintName + " CHECK (role IN ("
                    + enumValues
                    + "))");
        }
        logger.info("Updated user_authorization.role check constraint to match RoleCode");
    }

    private void deduplicateUserAuthorizations(Connection connection, String valueColumn) throws SQLException {
        String sql =
                "DELETE FROM user_authorization ua WHERE ua.id NOT IN ("
                        + "SELECT MIN(u2.id) FROM user_authorization u2 "
                        + "GROUP BY u2.email, u2."
                        + valueColumn
                        + ", u2.scope_type, u2.scope_id)";
        try (Statement st = connection.createStatement()) {
            int deleted = st.executeUpdate(sql);
            if (deleted > 0) {
                logger.info("Deduplicated {} user_authorization grant(s)", deleted);
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            ps.setString(1, informationSchemaName());
            ps.setString(2, informationSchemaTableName(table));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        return getTableColumns(connection, table).contains(column.toLowerCase(Locale.ROOT));
    }

    /** H2 (DATABASE_TO_LOWER=TRUE) and PostgreSQL both expose lowercase names in the {@code public} schema. */
    private String informationSchemaName() {
        return "public";
    }

    private String informationSchemaTableName(String table) {
        return table.toLowerCase(Locale.ROOT);
    }

    private Set<String> getTableColumns(Connection connection, String table) throws SQLException {
        String cacheKey = table.toLowerCase(Locale.ROOT);
        Set<String> cached = tableColumnsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> columns = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            ps.setString(1, informationSchemaName());
            ps.setString(2, informationSchemaTableName(table));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        tableColumnsCache.put(cacheKey, columns);
        return columns;
    }
}
