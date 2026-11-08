package com.github.gcolin.payment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Exports Stripe Checkout sessions to CSV. Usage: {@code StripeSession <apiKey> [out.csv]}
 */
public class StripeSession {

    public static void main(String[] args) throws StripeException, IOException {
        if (args.length < 1 || args[0].isBlank()) {
            System.err.println("Usage: StripeSession <stripe-secret-api-key> [out.csv]");
            System.exit(1);
        }

        String apiKey = args[0];
        String output = args.length > 1 ? args[1] : "out.csv";
        StripeCheckoutService stripe = new StripeCheckoutService();

        int limit = 100;
        String last = null;
        StringBuilder str = new StringBuilder();
        str.append("id,email,amount,intent\n");
        while (true) {
            StripeCheckoutClient.ListResult list = stripe.listSessions(apiKey, limit, last);
            List<StripeCheckoutSession> data = list.data();
            for (StripeCheckoutSession session : data) {
                str.append(session.getId()).append(",");
                str.append(session.getMetadata()).append(",");
                str.append(session.getAmountTotal()).append(",");
                str.append(session.getPaymentIntent()).append("\n");
            }
            if (list.hasMore() && !data.isEmpty()) {
                last = data.get(data.size() - 1).getId();
            } else {
                break;
            }
        }

        Files.write(Paths.get(output), str.toString().getBytes(StandardCharsets.UTF_8));
    }
}
