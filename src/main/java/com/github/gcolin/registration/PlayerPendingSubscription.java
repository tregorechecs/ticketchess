package com.github.gcolin.registration;

import com.github.gcolin.event.Event;
import com.github.gcolin.platform.TimedEntity;

public class PlayerPendingSubscription extends TimedEntity {

    private Integer id;

    private String nrFfe;

    private String creationUser;

    private Event event;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNrFfe() {
        return nrFfe;
    }

    public void setNrFfe(String nrFfe) {
        this.nrFfe = nrFfe;
    }

    public String getCreationUser() {
        return creationUser;
    }

    public void setCreationUser(String creationUser) {
        this.creationUser = creationUser;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }
}
