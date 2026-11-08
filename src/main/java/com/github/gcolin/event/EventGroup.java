package com.github.gcolin.event;

import com.github.gcolin.organisation.OrgOwned;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EventGroup implements OrgOwned {

    private Integer id;

    private Integer orgId;

    private String name;

    private String shortname;

    private List<Event> events = new ArrayList<>();

    /** Lazy loader installed by {@code EventGroupDao}; replaces JPA lazy loading of {@link #events}. */
    private transient Supplier<List<Event>> eventsLoader;

    // =========================
    // Constructeurs
    // =========================
    public EventGroup() {}

    public EventGroup(String name) {
        this.name = name;
    }

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

    public void setEvents(List<Event> events) {
        this.eventsLoader = null;
        this.events = events;
    }

    public List<Event> getEvents() {
        if (eventsLoader != null) {
            Supplier<List<Event>> loader = eventsLoader;
            eventsLoader = null;
            events = loader.get();
        }
        return events;
    }

    public void setEventsLoader(Supplier<List<Event>> eventsLoader) {
        this.eventsLoader = eventsLoader;
    }

    public String getShortname() {
        return shortname;
    }

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }
}
