package com.github.gcolin.payment;

import java.util.List;
import com.github.gcolin.membership.Membership;
import com.github.gcolin.platform.TimedEntity;
import com.github.gcolin.registration.PlayerSubscription;

public class Payment extends TimedEntity {

    private Long id;

    private String stripeSessionId;

    private String stripeIntent;

    private String userEmail;

    private String payerName;

    private String payerAddress;

    private boolean donation;

    private PaymentStatus status; // PENDING, PAID, EXPIRED

    private PaymentType type; // CARD, BANK_TRANSFER, FREE, CASH, CHEQUE

    private Double amount;

    private Long amountCents;

    private List<PlayerSubscription> subscriptions;

    private List<Membership> memberships;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public String getPayerAddress() {
        return payerAddress;
    }

    public void setPayerAddress(String payerAddress) {
        this.payerAddress = payerAddress;
    }

    public boolean isDonation() {
        return donation;
    }

    public void setDonation(boolean donation) {
        this.donation = donation;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType type) {
        this.type = type;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
        this.amountCents = amount == null ? null : Math.round(amount * 100d);
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
        this.amount = amountCents == null ? null : amountCents / 100d;
    }

    public List<PlayerSubscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<PlayerSubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<Membership> memberships) {
        this.memberships = memberships;
    }

    @Override
    public String toString() {
        return "Payment [id="
                + id
                + ", stripeSessionId="
                + stripeSessionId
                + ", userEmail="
                + userEmail
                + ", payerName="
                + payerName
                + ", payerAddress="
                + payerAddress
                + ", donation="
                + donation
                + ", status="
                + status
                + ", type="
                + type
                + ", amount="
                + amount
                + ", amountCents="
                + amountCents
                + ", subscriptions="
                + subscriptions
                + "]";
    }

    public String getStripeIntent() {
        return stripeIntent;
    }

    public void setStripeIntent(String stripeIntent) {
        this.stripeIntent = stripeIntent;
    }
}
