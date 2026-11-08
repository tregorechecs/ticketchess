package com.github.gcolin.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.dbutils.ResultSetHandler;

/**
 * Base DAO using Apache Commons DBUtils over the request-scoped JDBC connection.
 *
 * <p>Subclasses must implement table-specific SQL for persist/merge when the default is insufficient.
 */
public abstract class AbstractDao<T> {

    private final Class<T> type;
    private final String table;
    private final String idColumn;

    protected AbstractDao(Class<T> type, String table) {
        this(type, table, "id");
    }

    protected AbstractDao(Class<T> type, String table, String idColumn) {
        this.type = type;
        this.table = table;
        this.idColumn = idColumn;
    }

    protected Class<T> type() {
        return type;
    }

    protected String table() {
        return table;
    }

    protected String idColumn() {
        return idColumn;
    }

    /** Override to {@code true} for org-scoped tables (license, club_season, event, event_group, big_event). */
    protected boolean orgScoped() {
        return false;
    }

    protected String orgFilterSql(String alias) {
        return orgScoped() ? OrgFilter.sql(alias) : "";
    }

    protected void addOrgFilterArgs(List<Object> args) {
        if (orgScoped()) {
            OrgFilter.addArgs(args);
        }
    }

    /** Appends {@code keyword + org_id = ?} when the org filter is active. */
    protected void appendOrgFilter(StringBuilder sql, List<Object> args, String keyword) {
        appendOrgFilter(sql, args, keyword, null);
    }

    protected void appendOrgFilter(StringBuilder sql, List<Object> args, String keyword, String alias) {
        String filter = orgFilterSql(alias);
        if (!filter.isEmpty()) {
            sql.append(keyword).append(filter);
            addOrgFilterArgs(args);
        }
    }

    public List<T> all() {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);
        String filter = orgFilterSql(null);
        List<Object> args = Db.args();
        if (!filter.isEmpty()) {
            sql.append(" WHERE ").append(filter);
            addOrgFilterArgs(args);
        }
        return select(sql.toString(), args.toArray());
    }

    public PagedList<T> page(int start, int pageSize) {
        StringBuilder where = new StringBuilder();
        List<Object> args = Db.args();
        String filter = orgFilterSql(null);
        if (!filter.isEmpty()) {
            where.append(" WHERE ").append(filter);
            addOrgFilterArgs(args);
        }
        String countSql = "SELECT COUNT(*) FROM " + table + where;
        long total = Db.scalarLongOrZero(countSql, args.toArray());

        String listSql = "SELECT * FROM " + table + where + " ORDER BY " + idColumn + " LIMIT ? OFFSET ?";
        args.add(pageSize);
        args.add(start);
        List<T> list = select(listSql, args.toArray());
        return new PagedList<>(list, start, total);
    }

    public T find(Integer id) {
        if (id == null) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(table)
                .append(" WHERE ")
                .append(idColumn)
                .append(" = ?");
        List<Object> args = Db.args(id);
        String filter = orgFilterSql(null);
        if (!filter.isEmpty()) {
            sql.append(" AND ").append(filter);
            addOrgFilterArgs(args);
        }
        return first(select(sql.toString(), args.toArray()));
    }

    public T find(Long id) {
        if (id == null) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(table)
                .append(" WHERE ")
                .append(idColumn)
                .append(" = ?");
        List<Object> args = Db.args(id);
        String filter = orgFilterSql(null);
        if (!filter.isEmpty()) {
            sql.append(" AND ").append(filter);
            addOrgFilterArgs(args);
        }
        return first(select(sql.toString(), args.toArray()));
    }

    public void remove(Integer id) {
        T entity = find(id);
        if (entity != null) {
            remove(entity);
        }
    }

    public void remove(T entity) {
        if (entity == null) {
            return;
        }
        Object id = readId(entity);
        if (id == null) {
            return;
        }
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(table)
                .append(" WHERE ")
                .append(idColumn)
                .append(" = ?");
        List<Object> args = Db.args(id);
        String filter = orgFilterSql(null);
        if (!filter.isEmpty()) {
            sql.append(" AND ").append(filter);
            addOrgFilterArgs(args);
        }
        Db.update(sql.toString(), args.toArray());
    }

    /** Insert or update. Subclasses should override with concrete SQL. */
    public void persist(T entity) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " must implement persist()");
    }

    public T merge(T entity) {
        persist(entity);
        return entity;
    }

    public void flush() {
        // no-op: JDBC has no persistence context
    }

    public <X> List<X> detachAll(List<X> list) {
        return list;
    }

    public <X> X detach(X e) {
        return e;
    }

    protected void touchCreated(TimedEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
    }

    protected void touchUpdated(TimedEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
    }

    protected Object readId(T entity) {
        try {
            return type.getMethod("getId").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Cannot read id from " + type.getName(), e);
        }
    }

    protected void writeId(T entity, Object id) {
        try {
            Class<?> idType = type.getMethod("getId").getReturnType();
            Object coerced = id;
            if (idType == Integer.class || idType == int.class) {
                coerced = ((Number) id).intValue();
            } else if (idType == Long.class || idType == long.class) {
                coerced = ((Number) id).longValue();
            }
            type.getMethod("setId", idType).invoke(entity, coerced);
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Cannot set id on " + type.getName(), e);
        }
    }

    /**
     * Executes a SELECT and maps rows to entities. Subclasses with relationships override this to read
     * foreign keys and hydrate related entities.
     */
    protected List<T> select(String sql, Object... params) {
        return Db.queryList(sql, type, params);
    }

    protected T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    protected T selectOne(String sql, Object... params) {
        return first(select(sql, params));
    }

    /** Row callback reading extra columns (foreign keys) after bean mapping. */
    @FunctionalInterface
    public interface RowExtra<E> {
        void accept(E entity, ResultSet rs) throws SQLException;
    }

    /** Maps rows with the bean processor, then lets {@code extra} read additional columns. */
    protected List<T> selectWith(RowExtra<T> extra, String sql, Object... params) {
        ResultSetHandler<List<T>> handler = rs -> {
            List<T> list = new ArrayList<>();
            while (rs.next()) {
                T entity = Db.rowProcessor().toBean(rs, type);
                extra.accept(entity, rs);
                list.add(entity);
            }
            return list;
        };
        return Db.query(sql, handler, params);
    }

    /** Loads entities by integer id (no org filter), keyed by id. Used to hydrate many-to-one relations. */
    public java.util.Map<Integer, T> findByIds(Collection<Integer> ids) {
        java.util.Map<Integer, T> result = new java.util.LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (T entity : select(
                "SELECT * FROM " + table + " WHERE " + idColumn + " IN (" + placeholders(ids) + ")",
                ids.toArray())) {
            result.put(((Number) readId(entity)).intValue(), entity);
        }
        return result;
    }

    /** Same as {@link #findByIds(Collection)} for {@code Long} primary keys. */
    public java.util.Map<Long, T> findByLongIds(Collection<Long> ids) {
        java.util.Map<Long, T> result = new java.util.LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (T entity : select(
                "SELECT * FROM " + table + " WHERE " + idColumn + " IN (" + placeholders(ids) + ")",
                ids.toArray())) {
            result.put(((Number) readId(entity)).longValue(), entity);
        }
        return result;
    }

    /** {@code ?,?,?} placeholders for an IN clause. */
    public static String placeholders(Collection<?> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    protected static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    protected static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    protected static LocalDateTime timestampOrNull(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
