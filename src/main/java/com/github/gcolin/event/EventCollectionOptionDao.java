package com.github.gcolin.event;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.List;

public class EventCollectionOptionDao extends AbstractDao<EventCollectionOption> {

    public EventCollectionOptionDao() {
        super(EventCollectionOption.class, "eventcollectionoption");
    }

    @Override
    protected List<EventCollectionOption> select(String sql, Object... params) {
        List<EventCollectionOption> options = selectWith(
                (option, rs) -> {
                    Integer collectionId = intOrNull(rs, "big_event_id");
                    if (collectionId != null) {
                        EventCollection collection = new EventCollection();
                        collection.setId(collectionId);
                        option.setEventCollection(collection);
                    }
                },
                sql,
                params);
        return options;
    }

    public EventCollectionOption findByEventCollectionIdAndOptionType(
            int eventCollectionId, EventCollectionOptionType optionType) {
        return selectOne(
                "SELECT * FROM eventcollectionoption WHERE big_event_id = ? AND option_type = ? ORDER BY id LIMIT 1",
                eventCollectionId,
                optionType);
    }

    public void setOption(int eventCollectionId, EventCollectionOptionType optionType, String value) {
        EventCollectionOption option = findByEventCollectionIdAndOptionType(eventCollectionId, optionType);
        if (value == null || value.isBlank()) {
            if (option != null) {
                remove(option);
            }
            return;
        }
        if (option == null) {
            EventCollection eventCollection = new EventCollectionDao().find(eventCollectionId);
            option = new EventCollectionOption();
            option.setEventCollection(eventCollection);
            option.setOptionType(optionType);
            option.setValue(value);
            persist(option);
        } else {
            option.setValue(value);
            merge(option);
        }
    }

    public String findOptionValue(int eventCollectionId, EventCollectionOptionType optionType) {
        EventCollectionOption option = findByEventCollectionIdAndOptionType(eventCollectionId, optionType);
        return option == null ? null : option.getValue();
    }

    public Integer findIntOptionValue(int eventCollectionId, EventCollectionOptionType optionType) {
        String value = findOptionValue(eventCollectionId, optionType);
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
    public void persist(EventCollectionOption option) {
        if (option.getId() == null) {
            int id = Db.insertReturningInt(
                    "INSERT INTO eventcollectionoption (option_type, option_value, big_event_id) VALUES (?, ?, ?)",
                    option.getOptionType(),
                    option.getValue(),
                    Db.fkId(option.getEventCollection()));
            option.setId(id);
        } else {
            Db.update(
                    "UPDATE eventcollectionoption SET option_type = ?, option_value = ?, big_event_id = ? WHERE id = ?",
                    option.getOptionType(),
                    option.getValue(),
                    Db.fkId(option.getEventCollection()),
                    option.getId());
        }
    }

    @Override
    public EventCollectionOption merge(EventCollectionOption option) {
        persist(option);
        return option;
    }
}
