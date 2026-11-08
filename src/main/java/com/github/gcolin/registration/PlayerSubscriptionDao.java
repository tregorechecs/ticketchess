package com.github.gcolin.registration;

import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.dbutils.handlers.ColumnListHandler;

public class PlayerSubscriptionDao extends AbstractDao<PlayerSubscription> {

    private static final String JOIN_EVENT =
            "SELECT ps.* FROM player_subscription ps JOIN event e ON e.id = ps.event_id";

    public PlayerSubscriptionDao() {
        super(PlayerSubscription.class, "player_subscription");
    }

    @Override
    protected List<PlayerSubscription> select(String sql, Object... params) {
        return load(sql, true, params);
    }

    private List<PlayerSubscription> load(String sql, boolean withEvents, Object... params) {
        List<PlayerSubscription> list = selectWith(
                (sub, rs) -> {
                    Integer eventId = intOrNull(rs, "event_id");
                    if (eventId != null) {
                        Event event = new Event();
                        event.setId(eventId);
                        sub.setEvent(event);
                    }
                    Long paymentId = longOrNull(rs, "payment_id");
                    if (paymentId != null) {
                        Payment payment = new Payment();
                        payment.setId(paymentId);
                        sub.setPayment(payment);
                    }
                },
                sql,
                params);
        hydrate(list, withEvents);
        return list;
    }

    private void hydrate(List<PlayerSubscription> list, boolean withEvents) {
        if (list.isEmpty()) {
            return;
        }
        Set<Integer> eventIds = new LinkedHashSet<>();
        Set<Long> paymentIds = new LinkedHashSet<>();
        for (PlayerSubscription sub : list) {
            if (withEvents && sub.getEvent() != null) {
                eventIds.add(sub.getEvent().getId());
            }
            if (sub.getPayment() != null) {
                paymentIds.add(sub.getPayment().getId());
            }
        }
        Map<Integer, Event> events = withEvents ? new EventDao().findByIds(eventIds) : Map.of();
        Map<Long, Payment> payments = new PaymentDao().findByLongIds(paymentIds);
        for (PlayerSubscription sub : list) {
            if (withEvents && sub.getEvent() != null) {
                sub.setEvent(events.get(sub.getEvent().getId()));
            }
            if (sub.getPayment() != null) {
                sub.setPayment(payments.get(sub.getPayment().getId()));
            }
        }
    }

    public List<PlayerSubscription> findByCreationUser(String email) {
        return select("SELECT * FROM player_subscription WHERE creation_user = ?", email);
    }

    public List<PlayerSubscription> findByEvent(Event event) {
        List<PlayerSubscription> list =
                load("SELECT * FROM player_subscription WHERE event_id = ?", false, Db.fkId(event));
        for (PlayerSubscription sub : list) {
            sub.setEvent(event);
        }
        return list;
    }

    public List<PlayerSubscription> findByCreationUserWithEvents(String email) {
        return select("SELECT * FROM player_subscription WHERE creation_user = ?", email);
    }

    public PlayerSubscription findByEventAndNrffe(Event event, String nrffe) {
        List<PlayerSubscription> list = load(
                "SELECT * FROM player_subscription WHERE event_id = ? AND nr_ffe = ? AND status <> ? ORDER BY id LIMIT 1",
                false,
                Db.fkId(event),
                nrffe,
                Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
        PlayerSubscription sub = first(list);
        if (sub != null) {
            sub.setEvent(event);
        }
        return sub;
    }

    public long countByEvent(Event event) {
        return Db.scalarLongOrZero(
                "SELECT COUNT(*) FROM player_subscription WHERE event_id = ? AND status <> ?",
                Db.fkId(event),
                Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
    }

    public Map<Integer, Long> countByEventIds(List<Integer> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        List<Object> args = Db.args();
        args.addAll(eventIds);
        args.add(Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
        return Db.query(
                "SELECT event_id, COUNT(*) AS cnt FROM player_subscription WHERE event_id IN ("
                        + placeholders(eventIds)
                        + ") AND status <> ? GROUP BY event_id",
                rs -> {
                    Map<Integer, Long> counts = new HashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getInt("event_id"), rs.getLong("cnt"));
                    }
                    return counts;
                },
                args.toArray());
    }

    public long countByEventCollection(Integer eventCollectionId) {
        return Db.scalarLongOrZero(
                "SELECT COUNT(*) FROM player_subscription ps JOIN event e ON e.id = ps.event_id"
                        + " WHERE e.big_event_id = ? AND ps.status <> ?",
                eventCollectionId,
                Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
    }

    public PlayerSubscription findWithEvent(Integer id) {
        return find(id);
    }

    public List<PlayerSubscription> findByPaymentId(Integer payment_id) {
        return select("SELECT * FROM player_subscription WHERE payment_id = ?", payment_id == null ? null : payment_id.longValue());
    }

    public List<PlayerSubscription> findWithoutPaymentWithEvent(PlayerSubscriptionStatus status) {
        return findWithoutPaymentWithEvent(status, SeasonScope.all());
    }

    public List<PlayerSubscription> findWithoutPaymentWithEvent(PlayerSubscriptionStatus status, SeasonScope scope) {
        StringBuilder sql = new StringBuilder(JOIN_EVENT)
                .append(" WHERE ps.payment_id IS NULL AND COALESCE(e.pricecents, 0) > 0 AND ps.status = ?");
        List<Object> args = Db.args(Db.enumOrdinal(status));
        appendSeasonScope(sql, args, scope);
        return select(sql.toString(), args.toArray());
    }

    public List<PlayerSubscription> findCancelledWithEvent() {
        return findCancelledWithEvent(SeasonScope.all());
    }

    public List<PlayerSubscription> findCancelledWithEvent(SeasonScope scope) {
        StringBuilder sql = new StringBuilder(JOIN_EVENT).append(" WHERE ps.status = ?");
        List<Object> args = Db.args(Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
        appendSeasonScope(sql, args, scope);
        return select(sql.toString(), args.toArray());
    }

    public List<PlayerSubscription> findNotPaidWithEvent() {
        return findNotPaidWithEvent(SeasonScope.all());
    }

    public List<PlayerSubscription> findNotPaidWithEvent(SeasonScope scope) {
        StringBuilder sql = new StringBuilder(JOIN_EVENT).append(" WHERE ps.status = ?");
        List<Object> args = Db.args(Db.enumOrdinal(PlayerSubscriptionStatus.NOT_PAID));
        appendSeasonScope(sql, args, scope);
        return select(sql.toString(), args.toArray());
    }

    public List<String> findDistinctCreationUsers() {
        List<String> users = Db.query(
                "SELECT DISTINCT creation_user FROM player_subscription"
                        + " WHERE creation_user IS NOT NULL AND creation_user <> '' ORDER BY creation_user",
                new ColumnListHandler<String>());
        return users == null ? List.of() : users;
    }

    public List<PlayerSubscription> findLinkedToCustomPlayers() {
        return findLinkedToCustomPlayers(SeasonScope.all());
    }

    public List<PlayerSubscription> findLinkedToCustomPlayers(SeasonScope scope) {
        StringBuilder sql = new StringBuilder(JOIN_EVENT).append(" WHERE ps.nr_ffe LIKE ?");
        List<Object> args = Db.args("@%");
        appendSeasonScope(sql, args, scope);
        sql.append(" ORDER BY ps.created_at DESC");
        return select(sql.toString(), args.toArray());
    }

    public List<PlayerSubscription> findByEventCollection(Integer eventCollectionId) {
        return select(JOIN_EVENT + " WHERE e.big_event_id = ?", eventCollectionId);
    }

    public Set<String> findActiveRefsByEventCollection(Integer eventCollectionId) {
        List<String> refs = Db.query(
                "SELECT DISTINCT ps.nr_ffe FROM player_subscription ps JOIN event e ON e.id = ps.event_id"
                        + " WHERE e.big_event_id = ? AND ps.status <> ?",
                new ColumnListHandler<String>(),
                eventCollectionId,
                Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED));
        return refs == null ? new HashSet<>() : new HashSet<>(refs);
    }

    public boolean existsActiveByEventCollectionAndNrffe(Integer eventCollectionId, String nrffe) {
        return Db.scalarLongOrZero(
                        "SELECT COUNT(*) FROM player_subscription ps JOIN event e ON e.id = ps.event_id"
                                + " WHERE e.big_event_id = ? AND ps.nr_ffe = ? AND ps.status <> ?",
                        eventCollectionId,
                        nrffe,
                        Db.enumOrdinal(PlayerSubscriptionStatus.CANCELLED))
                > 0;
    }

    private static void appendSeasonScope(StringBuilder sql, List<Object> args, SeasonScope scope) {
        if (scope != null && scope.isFiltered()) {
            sql.append(" AND e.start_date >= ? AND e.start_date <= ?");
            args.add(scope.getStart());
            args.add(scope.getEnd());
        }
    }

    @Override
    public void persist(PlayerSubscription sub) {
        if (sub.getId() == null) {
            touchCreated(sub);
            int id = Db.insertReturningInt(
                    "INSERT INTO player_subscription (created_at, updated_at, nr_ffe, creation_user, event_id, status,"
                            + " payment_id, amountcents, attendance_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    sub.getCreatedAt(),
                    sub.getUpdatedAt(),
                    sub.getNrFfe(),
                    sub.getCreationUser(),
                    Db.fkId(sub.getEvent()),
                    Db.enumOrdinal(sub.getStatus()),
                    Db.fkLongId(sub.getPayment()),
                    sub.getAmountCents(),
                    sub.getAttendanceAt());
            sub.setId(id);
        } else {
            touchUpdated(sub);
            Db.update(
                    "UPDATE player_subscription SET updated_at = ?, nr_ffe = ?, creation_user = ?, event_id = ?,"
                            + " status = ?, payment_id = ?, amountcents = ?, attendance_at = ? WHERE id = ?",
                    sub.getUpdatedAt(),
                    sub.getNrFfe(),
                    sub.getCreationUser(),
                    Db.fkId(sub.getEvent()),
                    Db.enumOrdinal(sub.getStatus()),
                    Db.fkLongId(sub.getPayment()),
                    sub.getAmountCents(),
                    sub.getAttendanceAt(),
                    sub.getId());
        }
    }

    @Override
    public PlayerSubscription merge(PlayerSubscription sub) {
        persist(sub);
        return sub;
    }
}
