package com.github.gcolin.event;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.List;

public class EventGroupDao extends AbstractDao<EventGroup> {

    public EventGroupDao() {
        super(EventGroup.class, "event_group");
    }

    @Override
    protected boolean orgScoped() {
        return true;
    }

    @Override
    protected List<EventGroup> select(String sql, Object... params) {
        List<EventGroup> groups = super.select(sql, params);
        for (EventGroup group : groups) {
            Integer id = group.getId();
            group.setEventsLoader(() -> new EventDao().findByGroupId(id));
        }
        return groups;
    }

    public EventGroup findByShortname(String shortname) {
        StringBuilder sql = new StringBuilder("SELECT * FROM event_group WHERE shortname = ?");
        List<Object> args = Db.args(shortname);
        appendOrgFilter(sql, args, " AND ");
        sql.append(" ORDER BY id LIMIT 1");
        return selectOne(sql.toString(), args.toArray());
    }

    public EventGroup findDetachedForEdit(int id) {
        EventGroup eventGroup = find(id);
        eventGroup.setEvents(detachAll(eventGroup.getEvents()));
        return detach(eventGroup);
    }

    @Override
    public void persist(EventGroup group) {
        if (group.getId() == null) {
            OrgEntityListener.assignOrg(group);
            int id = Db.insertReturningInt(
                    "INSERT INTO event_group (org_id, name, shortname) VALUES (?, ?, ?)",
                    group.getOrgId(),
                    group.getName(),
                    group.getShortname());
            group.setId(id);
        } else {
            Db.update(
                    "UPDATE event_group SET org_id = ?, name = ?, shortname = ? WHERE id = ?",
                    group.getOrgId(),
                    group.getName(),
                    group.getShortname(),
                    group.getId());
        }
    }

    @Override
    public EventGroup merge(EventGroup group) {
        persist(group);
        return group;
    }
}
