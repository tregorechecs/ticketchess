package com.github.gcolin.event;

import com.github.gcolin.organisation.OrgOwned;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EventCollection implements OrgOwned {

    private Integer id;

    private Integer orgId;

    private String name;

    private List<Event> events = new ArrayList<>();

    /** Lazy loader installed by {@code EventCollectionDao}; replaces JPA lazy loading of {@link #events}. */
    private transient Supplier<List<Event>> eventsLoader;

    private Integer maxSubscribe;

    private Integer nbSubscriptions;

    private Integer eventCount;

    // =========================
    // Constructeurs
    // =========================
    public EventCollection() {}

    // =========================
    // Getters et Setters
    // =========================
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getOrgId() {
        return orgId;
    }

    @Override
    public void setOrgId(Integer orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Event> getEvents() {
        if (eventsLoader != null) {
            Supplier<List<Event>> loader = eventsLoader;
            eventsLoader = null;
            events = loader.get();
        }
        return events;
    }

    public void setEvents(List<Event> events) {
        this.eventsLoader = null;
        this.events = events;
    }

    public void setEventsLoader(Supplier<List<Event>> eventsLoader) {
        this.eventsLoader = eventsLoader;
    }

    public Integer getMaxSubscribe() {
        return maxSubscribe;
    }

    public void setMaxSubscribe(Integer maxSubscribe) {
        this.maxSubscribe = maxSubscribe;
    }

    public Integer getNbSubscriptions() {
        return nbSubscriptions;
    }

    public void setNbSubscriptions(Integer nbSubscriptions) {
        this.nbSubscriptions = nbSubscriptions;
    }

    public int getEventCount() {
        if (eventCount != null) {
            return eventCount;
        }
        return events != null ? events.size() : 0;
    }

    public void setEventCount(Integer eventCount) {
        this.eventCount = eventCount;
    }

    @Override
    public String toString() {
        return "EventCollection [id=" + id + ", name=" + name + ", eventCount=" + getEventCount() + "]";
    }
}
