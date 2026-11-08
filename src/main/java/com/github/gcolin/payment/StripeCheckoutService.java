package com.github.gcolin.payment;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Application-facing Stripe Checkout API. Builds a fresh {@link StripeCheckoutClient} for each
 * call so HTTP resources are not held by JAX-RS resources.
 */
public class StripeCheckoutService {

    public StripeCheckoutSession createPaymentSession(
            String apiKey,
            String idempotencyKey,
            long amountCents,
            String productName,
            String customerEmail,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata)
            throws StripeException {
        return client().createPaymentSession(
                apiKey,
                idempotencyKey,
                amountCents,
                productName,
                customerEmail,
                successUrl,
                cancelUrl,
                metadata);
    }

    public StripeCheckoutSession retrieveSession(String apiKey, String sessionId) throws StripeException {
        return client().retrieveSession(apiKey, sessionId);
    }

    public StripeCheckoutClient.ListResult listSessions(String apiKey, int limit, String startingAfter)
            throws StripeException {
        return client().listSessions(apiKey, limit, startingAfter);
    }

    /** Creates the HTTP client only when a Stripe call is about to be made. */
    protected StripeCheckoutClient client() {
        return new StripeCheckoutClient(HttpClient.newHttpClient());
    }
}
