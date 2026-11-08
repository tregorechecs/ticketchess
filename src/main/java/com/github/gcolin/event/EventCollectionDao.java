package com.github.gcolin.event;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import java.util.List;
import java.util.function.Supplier;

public class EventCollectionDao extends AbstractDao<EventCollection> {

    private Supplier<EventCollectionOptionDao> eventCollectionOptionDao;
    private Supplier<PlayerSubscriptionDao> playerSubscriptionDao;

    public EventCollectionDao() {
        super(EventCollection.class, "big_event");
    }

    @Override
    protected boolean orgScoped() {
        return true;
    }

    public void setEventCollectionOptionDao(Supplier<EventCollectionOptionDao> eventCollectionOptionDao) {
        this.eventCollectionOptionDao = eventCollectionOptionDao;
    }

    public void setPlayerSubscriptionDao(Supplier<PlayerSubscriptionDao> playerSubscriptionDao) {
        this.playerSubscriptionDao = playerSubscriptionDao;
    }

    @Override
    protected List<EventCollection> select(String sql, Object... params) {
        List<EventCollection> collections = super.select(sql, params);
        for (EventCollection collection : collections) {
            Integer id = collection.getId();
            collection.setEventsLoader(() -> new EventDao().findByCollectionId(id));
        }
        return collections;
    }

    public List<EventCollection> allOrdered() {
        StringBuilder sql = new StringBuilder("SELECT * FROM big_event");
        List<Object> args = Db.args();
        appendOrgFilter(sql, args, " WHERE ");
        sql.append(" ORDER BY name ASC");
        List<EventCollection> collections = select(sql.toString(), args.toArray());
        collections.forEach(this::fillSubscriptionLimits);
        return collections;
    }

    public long countLinkedEvents(Integer id) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM event WHERE big_event_id = ?");
        List<Object> args = Db.args(id);
        appendOrgFilter(sql, args, " AND ");
        return Db.scalarLongOrZero(sql.toString(), args.toArray());
    }

    public void fillSubscriptionLimits(EventCollection eventCollection) {
        if (eventCollection == null || eventCollection.getId() == null) {
            return;
        }
        eventCollection.setMaxSubscribe(
                eventCollectionOptionDao.get().findIntOptionValue(eventCollection.getId(), EventCollectionOptionType.MAX_SUBSCRIPTIONS));
        eventCollection.setNbSubscriptions((int) playerSubscriptionDao.get().countByEventCollection(eventCollection.getId()));
        eventCollection.setEventCount((int) countLinkedEvents(eventCollection.getId()));
    }

    @Override
    public void persist(EventCollection collection) {
        if (collection.getId() == null) {
            OrgEntityListener.assignOrg(collection);
            int id = Db.insertReturningInt(
                    "INSERT INTO big_event (org_id, name) VALUES (?, ?)",
                    collection.getOrgId(),
                    collection.getName());
            collection.setId(id);
        } else {
            Db.update(
                    "UPDATE big_event SET org_id = ?, name = ? WHERE id = ?",
                    collection.getOrgId(),
                    collection.getName(),
                    collection.getId());
        }
    }

    @Override
    public EventCollection merge(EventCollection collection) {
        persist(collection);
        return collection;
    }
}
