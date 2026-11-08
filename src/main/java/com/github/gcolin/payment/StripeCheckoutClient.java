package com.github.gcolin.payment;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Minimal Stripe Checkout Sessions client based on {@link HttpClient} and {@code org.json}.
 * Covers create / retrieve / list used by Ticket Chess.
 */
public class StripeCheckoutClient {

    private static final URI SESSIONS_URI = URI.create("https://api.stripe.com/v1/checkout/sessions");

    private final HttpClient httpClient;

    public StripeCheckoutClient() {
        this(HttpClient.newHttpClient());
    }

    public StripeCheckoutClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

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
        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "payment");
        form.put("line_items[0][price_data][currency]", "eur");
        form.put("line_items[0][price_data][unit_amount]", Long.toString(amountCents));
        form.put("line_items[0][price_data][product_data][name]", productName);
        form.put("line_items[0][quantity]", "1");
        form.put("success_url", successUrl);
        form.put("cancel_url", cancelUrl);
        form.put("customer_email", customerEmail);
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    form.put("metadata[" + entry.getKey() + "]", entry.getValue());
                }
            }
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(SESSIONS_URI)
                .header("Authorization", bearer(apiKey))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return parseSession(send(builder.build()));
    }

    public StripeCheckoutSession retrieveSession(String apiKey, String sessionId) throws StripeException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new StripeException("session id is required");
        }
        URI uri = URI.create(SESSIONS_URI.toString() + "/" + encodePathSegment(sessionId));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", bearer(apiKey))
                .GET()
                .build();
        return parseSession(send(request));
    }

    public ListResult listSessions(String apiKey, int limit, String startingAfter) throws StripeException {
        StringBuilder query = new StringBuilder("?limit=").append(Math.max(1, Math.min(limit, 100)));
        if (startingAfter != null && !startingAfter.isBlank()) {
            query.append("&starting_after=")
                    .append(URLEncoder.encode(startingAfter, StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SESSIONS_URI.toString() + query))
                .header("Authorization", bearer(apiKey))
                .GET()
                .build();
        JSONObject json = send(request);
        JSONArray data = json.optJSONArray("data");
        List<StripeCheckoutSession> sessions = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                sessions.add(parseSessionObject(data.getJSONObject(i)));
            }
        }
        return new ListResult(sessions, json.optBoolean("has_more", false));
    }

    private JSONObject send(HttpRequest request) throws StripeException {
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body();
            JSONObject json;
            try {
                json = body.isBlank() ? new JSONObject() : new JSONObject(body);
            } catch (Exception parseError) {
                throw new StripeException(
                        "Invalid Stripe response (" + response.statusCode() + "): " + body,
                        response.statusCode(),
                        parseError);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = extractErrorMessage(json, body);
                throw new StripeException(message, response.statusCode());
            }
            return json;
        } catch (IOException e) {
            throw new StripeException("Stripe request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StripeException("Stripe request interrupted", e);
        }
    }

    private static StripeCheckoutSession parseSession(JSONObject json) {
        return parseSessionObject(json);
    }

    private static StripeCheckoutSession parseSessionObject(JSONObject json) {
        Map<String, String> metadata = new LinkedHashMap<>();
        JSONObject meta = json.optJSONObject("metadata");
        if (meta != null) {
            for (String key : meta.keySet()) {
                Object value = meta.opt(key);
                if (value != null && value != JSONObject.NULL) {
                    metadata.put(key, String.valueOf(value));
                }
            }
        }
        Long amountTotal = json.has("amount_total") && !json.isNull("amount_total")
                ? json.getLong("amount_total")
                : null;
        return new StripeCheckoutSession(
                json.optString("id", null),
                json.optString("status", null),
                json.optString("payment_status", null),
                paymentIntentId(json.opt("payment_intent")),
                amountTotal,
                metadata);
    }

    private static String paymentIntentId(Object paymentIntent) {
        if (paymentIntent == null || paymentIntent == JSONObject.NULL) {
            return null;
        }
        if (paymentIntent instanceof JSONObject object) {
            String id = object.optString("id", null);
            return id == null || id.isBlank() ? null : id;
        }
        String id = String.valueOf(paymentIntent);
        return id.isBlank() || "null".equals(id) ? null : id;
    }

    private static String extractErrorMessage(JSONObject json, String rawBody) {
        JSONObject error = json.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message", null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        if (rawBody != null && !rawBody.isBlank()) {
            return rawBody;
        }
        return "Stripe API error";
    }

    private static String bearer(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("stripe.secret is required");
        }
        return "Bearer " + apiKey;
    }

    private static String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Encode a single path segment without turning spaces into {@code +}. */
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record ListResult(List<StripeCheckoutSession> data, boolean hasMore) {}
}
