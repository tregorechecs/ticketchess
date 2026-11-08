package com.github.gcolin.platform;

import java.beans.PropertyDescriptor;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.GenerousBeanProcessor;
import org.apache.commons.dbutils.BasicRowProcessor;

/**
 * Maps JDBC rows to beans: snake_case / concatenated EclipseLink names → camelCase properties,
 * plus enum (STRING or ORDINAL) and Java time conversions.
 */
public final class TicketRowProcessor extends BasicRowProcessor {

    /** Explicit overrides where normalize(column) ≠ normalize(property). Prefer SQL aliases when ambiguous. */
    private static final Map<String, String> COLUMN_OVERRIDES = Map.ofEntries(
            Map.entry("stripesessionid", "stripeSessionId"),
            Map.entry("stripeintent", "stripeIntent"),
            Map.entry("useremail", "userEmail"),
            Map.entry("payername", "payerName"),
            Map.entry("payeraddress", "payerAddress"),
            Map.entry("amountcents", "amountCents"),
            Map.entry("pricecents", "priceCents"),
            Map.entry("youngprice", "youngPrice"),
            Map.entry("youngpricecents", "youngPriceCents"),
            Map.entry("eventid", "id"),
            Map.entry("is_current", "current"),
            Map.entry("option_value", "value"),
            Map.entry("creation_user", "user"),
            Map.entry("iscurrent", "current"),
            Map.entry("status_id", "status"),
            Map.entry("birthdate", "birthDate"),
            Map.entry("statusid", "status"));

    public TicketRowProcessor() {
        super(new TicketBeanProcessor());
    }

    static final class TicketBeanProcessor extends GenerousBeanProcessor {

        @Override
        protected int[] mapColumnsToProperties(ResultSetMetaData rsmd, PropertyDescriptor[] props)
                throws SQLException {
            int cols = rsmd.getColumnCount();
            int[] columnToProperty = new int[cols + 1];
            Arrays.fill(columnToProperty, PROPERTY_NOT_FOUND);

            Map<String, Integer> propertyByKey = new HashMap<>();
            for (int i = 0; i < props.length; i++) {
                String name = props[i].getName();
                if (name == null) {
                    continue;
                }
                propertyByKey.put(normalize(name), i);
            }

            for (int col = 1; col <= cols; col++) {
                String label = rsmd.getColumnLabel(col);
                if (label == null || label.isBlank()) {
                    label = rsmd.getColumnName(col);
                }
                String key = normalize(label);
                // Overrides match the raw lower-cased label so that e.g. "event_id" (a foreign key)
                // is not confused with a similarly named primary-key-style label.
                String override = COLUMN_OVERRIDES.get(label.toLowerCase(Locale.ROOT));
                if (override != null) {
                    Integer idx = propertyByKey.get(normalize(override));
                    if (idx != null) {
                        columnToProperty[col] = idx;
                        continue;
                    }
                }
                Integer idx = propertyByKey.get(key);
                if (idx == null) {
                    // GenerousBeanProcessor style: strip underscores already in normalize
                    idx = propertyByKey.get(key.replace("_", ""));
                }
                if (idx != null) {
                    columnToProperty[col] = idx;
                }
            }
            return columnToProperty;
        }

        @Override
        protected Object processColumn(ResultSet rs, int index, Class<?> propType) throws SQLException {
            if (propType.isEnum()) {
                return readEnum(rs, index, propType);
            }
            if (propType == LocalDateTime.class) {
                Timestamp ts = rs.getTimestamp(index);
                return ts == null ? null : ts.toLocalDateTime();
            }
            if (propType == LocalDate.class) {
                java.sql.Date d = rs.getDate(index);
                return d == null ? null : d.toLocalDate();
            }
            return super.processColumn(rs, index, propType);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object readEnum(ResultSet rs, int index, Class<?> propType) throws SQLException {
            Object raw = rs.getObject(index);
            if (raw == null) {
                return null;
            }
            Class<? extends Enum> enumType = (Class<? extends Enum>) propType;
            if (raw instanceof Number n) {
                return enumType.getEnumConstants()[n.intValue()];
            }
            String name = String.valueOf(raw).trim();
            if (name.isEmpty()) {
                return null;
            }
            return Enum.valueOf(enumType, name);
        }

        private static String normalize(String name) {
            return name.toLowerCase(Locale.ROOT).replace("_", "");
        }
    }
}
