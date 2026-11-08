package com.github.gcolin.registration;

import com.github.gcolin.registration.PlayerSubscriptionOptionStatus;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.platform.TimedEntity;

public class PlayerSubscriptionOption extends TimedEntity {

    private Integer id;

    private PlayerSubscription playerSubscription;

    private Long amountCents;

    private String description;

    private Payment payment;

    private PlayerSubscriptionOptionStatus status;

    public PlayerSubscriptionOption() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public PlayerSubscription getPlayerSubscription() {
        return playerSubscription;
    }

    public void setPlayerSubscription(PlayerSubscription playerSubscription) {
        this.playerSubscription = playerSubscription;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public PlayerSubscriptionOptionStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerSubscriptionOptionStatus status) {
        this.status = status;
    }
}
