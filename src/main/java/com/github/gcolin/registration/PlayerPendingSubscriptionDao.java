package com.github.gcolin.registration;

import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerPendingSubscriptionDao extends AbstractDao<PlayerPendingSubscription> {

    private static final String JOIN_EVENT =
            "SELECT p.* FROM player_pending_subscription p JOIN event e ON e.id = p.event_id";

    public PlayerPendingSubscriptionDao() {
        super(PlayerPendingSubscription.class, "player_pending_subscription");
    }

    @Override
    protected List<PlayerPendingSubscription> select(String sql, Object... params) {
        return load(sql, true, params);
    }

    private List<PlayerPendingSubscription> load(String sql, boolean withEvents, Object... params) {
        List<PlayerPendingSubscription> list = selectWith(
                (pending, rs) -> {
                    Integer eventId = intOrNull(rs, "event_id");
                    if (eventId != null) {
                        Event event = new Event();
                        event.setId(eventId);
                        pending.setEvent(event);
                    }
                },
                sql,
                params);
        if (withEvents && !list.isEmpty()) {
            Set<Integer> eventIds = new LinkedHashSet<>();
            for (PlayerPendingSubscription pending : list) {
                if (pending.getEvent() != null) {
                    eventIds.add(pending.getEvent().getId());
                }
            }
            Map<Integer, Event> events = new EventDao().findByIds(eventIds);
            for (PlayerPendingSubscription pending : list) {
                if (pending.getEvent() != null) {
                    pending.setEvent(events.get(pending.getEvent().getId()));
                }
            }
        }
        return list;
    }

    private PlayerPendingSubscription withEvent(PlayerPendingSubscription pending, Event event) {
        if (pending != null) {
            pending.setEvent(event);
        }
        return pending;
    }

    public PlayerPendingSubscription findByEventAndNrffe(Event event, String nrffe) {
        return withEvent(
                first(load(
                        "SELECT * FROM player_pending_subscription WHERE event_id = ? AND nr_ffe = ? ORDER BY id LIMIT 1",
                        false,
                        Db.fkId(event),
                        nrffe)),
                event);
    }

    public PlayerPendingSubscription findOldestByEvent(Event event) {
        return withEvent(
                first(load(
                        "SELECT * FROM player_pending_subscription WHERE event_id = ?"
                                + " ORDER BY created_at ASC, id ASC LIMIT 1",
                        false,
                        Db.fkId(event))),
                event);
    }

    public List<PlayerPendingSubscription> findByEvent(Event event) {
        List<PlayerPendingSubscription> list = load(
                "SELECT * FROM player_pending_subscription WHERE event_id = ? ORDER BY created_at ASC, id ASC",
                false,
                Db.fkId(event));
        list.forEach(p -> p.setEvent(event));
        return list;
    }

    public List<PlayerPendingSubscription> findAllWithEvent() {
        return findAllWithEvent(SeasonScope.all());
    }

    public List<PlayerPendingSubscription> findAllWithEvent(SeasonScope scope) {
        StringBuilder sql = new StringBuilder(JOIN_EVENT);
        List<Object> args = Db.args();
        if (scope.isFiltered()) {
            sql.append(" WHERE e.start_date >= ? AND e.start_date <= ?");
            args.add(scope.getStart());
            args.add(scope.getEnd());
        }
        sql.append(" ORDER BY p.created_at ASC, p.id ASC");
        return select(sql.toString(), args.toArray());
    }

    public List<PlayerPendingSubscription> findByEventCollection(Integer eventCollectionId) {
        return select(
                JOIN_EVENT + " WHERE e.big_event_id = ? ORDER BY p.created_at ASC, p.id ASC", eventCollectionId);
    }

    public List<PlayerPendingSubscription> findByCreationUserWithEvent(String email) {
        return select(
                JOIN_EVENT + " WHERE p.creation_user = ? ORDER BY p.event_id ASC, p.created_at ASC, p.id ASC", email);
    }

    public PlayerPendingSubscription findByEventAndNrffeAndCreationUser(Event event, String nrffe, String creationUser) {
        return withEvent(
                first(load(
                        "SELECT * FROM player_pending_subscription WHERE event_id = ? AND nr_ffe = ?"
                                + " AND creation_user = ? ORDER BY id LIMIT 1",
                        false,
                        Db.fkId(event),
                        nrffe,
                        creationUser)),
                event);
    }

    @Override
    public void persist(PlayerPendingSubscription pending) {
        if (pending.getId() == null) {
            touchCreated(pending);
            int id = Db.insertReturningInt(
                    "INSERT INTO player_pending_subscription (created_at, updated_at, nr_ffe, creation_user, event_id)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    pending.getCreatedAt(),
                    pending.getUpdatedAt(),
                    pending.getNrFfe(),
                    pending.getCreationUser(),
                    Db.fkId(pending.getEvent()));
            pending.setId(id);
        } else {
            touchUpdated(pending);
            Db.update(
                    "UPDATE player_pending_subscription SET updated_at = ?, nr_ffe = ?, creation_user = ?,"
                            + " event_id = ? WHERE id = ?",
                    pending.getUpdatedAt(),
                    pending.getNrFfe(),
                    pending.getCreationUser(),
                    Db.fkId(pending.getEvent()),
                    pending.getId());
        }
    }

    @Override
    public PlayerPendingSubscription merge(PlayerPendingSubscription pending) {
        persist(pending);
        return pending;
    }
}
