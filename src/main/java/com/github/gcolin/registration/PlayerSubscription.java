package com.github.gcolin.registration;

import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import java.time.LocalDateTime;
import com.github.gcolin.event.Event;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.platform.TimedEntity;

public class PlayerSubscription extends TimedEntity {

    private Integer id;

    private String nrFfe;

    private String creationUser;

    private Event event;

    private PlayerSubscriptionStatus status;

    private Payment payment;

    private Long amountCents;

    private LocalDateTime attendanceAt;

    private DisplayPlayer player;

    // Constructeurs
    public PlayerSubscription() {}

    public DisplayPlayer getPlayer() {
        return player;
    }

    public void setDisplayPlayer(DisplayPlayer player) {
        this.player = player;
    }

    // Getters et Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
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

    public PlayerSubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerSubscriptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getAttendanceAt() {
        return attendanceAt;
    }

    public void setAttendanceAt(LocalDateTime attendanceAt) {
        this.attendanceAt = attendanceAt;
    }
}
