package com.github.gcolin.payment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Subset of a Stripe Checkout Session used by Ticket Chess. */
public class StripeCheckoutSession {

    private final String id;
    private final String status;
    private final String paymentStatus;
    private final String paymentIntent;
    private final Long amountTotal;
    private final Map<String, String> metadata;

    public StripeCheckoutSession(
            String id,
            String status,
            String paymentStatus,
            String paymentIntent,
            Long amountTotal,
            Map<String, String> metadata) {
        this.id = id;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.paymentIntent = paymentIntent;
        this.amountTotal = amountTotal;
        this.metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getPaymentIntent() {
        return paymentIntent;
    }

    public Long getAmountTotal() {
        return amountTotal;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
