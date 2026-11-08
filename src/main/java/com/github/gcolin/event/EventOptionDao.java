package com.github.gcolin.event;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.List;
import java.util.function.Supplier;

public class EventOptionDao extends AbstractDao<EventOption> {

    private Supplier<EventDao> eventDao;

    public EventOptionDao() {
        super(EventOption.class, "eventoption");
    }

    public void setEventDao(Supplier<EventDao> eventDao) {
        this.eventDao = eventDao;
    }

    /** Options only carry the owning event id (a reference), not a fully loaded event. */
    @Override
    protected List<EventOption> select(String sql, Object... params) {
        return selectWith(
                (option, rs) -> {
                    Integer eventId = intOrNull(rs, "event_id");
                    if (eventId != null) {
                        Event event = new Event();
                        event.setId(eventId);
                        option.setEvent(event);
                    }
                },
                sql,
                params);
    }

    public List<EventOption> findByEventId(int eventId) {
        return select("SELECT * FROM eventoption WHERE event_id = ? ORDER BY id", eventId);
    }

    public EventOption findByEventIdAndOptionType(int eventId, EventOptionType optionType) {
        return selectOne(
                "SELECT * FROM eventoption WHERE event_id = ? AND option_type = ? ORDER BY id LIMIT 1",
                eventId,
                optionType);
    }

    public void setOption(int eventId, EventOptionType optionType, String value) {
        Event event = eventDao.get().find(eventId);
        EventOption option = findByEventIdAndOptionType(eventId, optionType);
        if (option == null) {
            option = new EventOption();
            option.setEvent(event);
            option.setOptionType(optionType);
            option.setValue(value);
            persist(option);
        } else {
            option.setValue(value);
            merge(option);
        }
    }

    public String findOptionValue(int eventId, EventOptionType optionType) {
        EventOption option = findByEventIdAndOptionType(eventId, optionType);
        return option == null ? null : option.getValue();
    }

    public Integer findIntOptionValue(int eventId, EventOptionType optionType) {
        String value = findOptionValue(eventId, optionType);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void persist(EventOption option) {
        if (option.getId() == null) {
            int id = Db.insertReturningInt(
                    "INSERT INTO eventoption (option_type, option_value, event_id) VALUES (?, ?, ?)",
                    option.getOptionType(),
                    option.getValue(),
                    Db.fkId(option.getEvent()));
            option.setId(id);
        } else {
            Db.update(
                    "UPDATE eventoption SET option_type = ?, option_value = ?, event_id = ? WHERE id = ?",
                    option.getOptionType(),
                    option.getValue(),
                    Db.fkId(option.getEvent()),
                    option.getId());
        }
    }

    @Override
    public EventOption merge(EventOption option) {
        persist(option);
        return option;
    }
}
