package com.github.gcolin.platform;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

/** Thin JDBC / DBUtils helpers bound to a request {@link Connection}. */
public final class Db {

    private static final QueryRunner RUNNER = new QueryRunner();
    private static final TicketRowProcessor ROW_PROCESSOR = new TicketRowProcessor();

    private Db() {}

    public static QueryRunner runner() {
        return RUNNER;
    }

    public static TicketRowProcessor rowProcessor() {
        return ROW_PROCESSOR;
    }

    public static Connection connection() {
        return RequestContext.require().connection();
    }

    public static <T> BeanHandler<T> beanHandler(Class<T> type) {
        return new BeanHandler<>(type, ROW_PROCESSOR);
    }

    public static <T> BeanListHandler<T> beanListHandler(Class<T> type) {
        return new BeanListHandler<>(type, ROW_PROCESSOR);
    }

    public static <T> T query(String sql, ResultSetHandler<T> handler, Object... params) {
        try {
            return RUNNER.query(connection(), sql, handler, jdbcParams(params));
        } catch (SQLException e) {
            throw new PersistenceException("Query failed: " + sql, e);
        }
    }

    public static <T> T queryOne(String sql, Class<T> type, Object... params) {
        return query(sql, beanHandler(type), params);
    }

    public static <T> List<T> queryList(String sql, Class<T> type, Object... params) {
        List<T> list = query(sql, beanListHandler(type), params);
        return list == null ? List.of() : list;
    }

    public static int update(String sql, Object... params) {
        try {
            return RUNNER.update(connection(), sql, jdbcParams(params));
        } catch (SQLException e) {
            throw new PersistenceException("Update failed: " + sql, e);
        }
    }

    public static long insertReturningLong(String sql, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParams(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new PersistenceException("No generated key for: " + sql);
                }
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Insert failed: " + sql, e);
        }
    }

    /**
     * QueryRunner uses {@code setObject}; H2 rejects Java enums as {@code JAVA_OBJECT} against VARCHAR.
     * Convert enums / java.time to JDBC-friendly values. Ordinal enums must already be passed as
     * {@link Integer} via {@link #enumOrdinal(Enum)}.
     */
    static Object[] jdbcParams(Object... params) {
        if (params == null || params.length == 0) {
            return params;
        }
        Object[] out = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            out[i] = toJdbcValue(params[i]);
        }
        return out;
    }

    static Object toJdbcValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (value instanceof LocalDate ld) {
            return java.sql.Date.valueOf(ld);
        }
        return value;
    }

    public static int insertReturningInt(String sql, Object... params) {
        return (int) insertReturningLong(sql, params);
    }

    public static Long scalarLong(String sql, Object... params) {
        Number n = query(sql, new ScalarHandler<>(), params);
        return n == null ? null : n.longValue();
    }

    public static long scalarLongOrZero(String sql, Object... params) {
        Long n = scalarLong(sql, params);
        return n == null ? 0L : n;
    }

    public static Integer scalarInt(String sql, Object... params) {
        Number n = query(sql, new ScalarHandler<>(), params);
        return n == null ? null : n.intValue();
    }

    public static void setParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            setParam(ps, i + 1, params[i]);
        }
    }

    public static void setParam(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (value instanceof Enum<?> e) {
            // Callers decide ordinal vs name; default to name for STRING enums.
            // For ORDINAL, pass Integer explicitly.
            ps.setString(index, e.name());
            return;
        }
        if (value instanceof LocalDateTime ldt) {
            ps.setTimestamp(index, Timestamp.valueOf(ldt));
            return;
        }
        if (value instanceof LocalDate ld) {
            ps.setDate(index, java.sql.Date.valueOf(ld));
            return;
        }
        if (value instanceof BigDecimal bd) {
            ps.setBigDecimal(index, bd);
            return;
        }
        ps.setObject(index, value);
    }

    public static Object enumOrdinal(Enum<?> value) {
        return value == null ? null : value.ordinal();
    }

    public static Object enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    public static Integer fkId(Object entityWithId) {
        if (entityWithId == null) {
            return null;
        }
        try {
            Object id = entityWithId.getClass().getMethod("getId").invoke(entityWithId);
            if (id == null) {
                return null;
            }
            if (id instanceof Integer i) {
                return i;
            }
            if (id instanceof Long l) {
                return l.intValue();
            }
            if (id instanceof Number n) {
                return n.intValue();
            }
            throw new PersistenceException("Unsupported id type: " + id.getClass());
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Cannot read id from " + entityWithId.getClass().getName(), e);
        }
    }

    public static Long fkLongId(Object entityWithId) {
        if (entityWithId == null) {
            return null;
        }
        try {
            Object id = entityWithId.getClass().getMethod("getId").invoke(entityWithId);
            if (id == null) {
                return null;
            }
            if (id instanceof Long l) {
                return l;
            }
            if (id instanceof Number n) {
                return n.longValue();
            }
            throw new PersistenceException("Unsupported id type: " + id.getClass());
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Cannot read id from " + entityWithId.getClass().getName(), e);
        }
    }

    public static List<Object> args(Object... values) {
        List<Object> list = new ArrayList<>(values.length);
        for (Object value : values) {
            list.add(value);
        }
        return list;
    }

    public static void setNullInt(PreparedStatement ps, int index) throws SQLException {
        ps.setNull(index, Types.INTEGER);
    }
}
