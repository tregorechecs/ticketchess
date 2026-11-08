package com.github.gcolin.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripeCheckoutClientTest {

    @Test
    void createPaymentSessionPostsFormAndParsesResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        """
                        {
                          "id":"cs_test_1",
                          "status":"open",
                          "payment_status":"unpaid",
                          "payment_intent":"pi_1",
                          "amount_total":1500,
                          "metadata":{"email":"a@b.c","scope":"event"}
                        }
                        """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StripeCheckoutClient client = new StripeCheckoutClient(http);
        StripeCheckoutSession session = client.createPaymentSession(
                "sk_test",
                "idem-1",
                1500L,
                "Commande Alice",
                "a@b.c",
                "https://example/success?session_id={CHECKOUT_SESSION_ID}",
                "https://example/cancel",
                Map.of("email", "a@b.c", "scope", "event"));

        assertEquals("cs_test_1", session.getId());
        assertEquals("open", session.getStatus());
        assertEquals("unpaid", session.getPaymentStatus());
        assertEquals("pi_1", session.getPaymentIntent());
        assertEquals(1500L, session.getAmountTotal());
        assertEquals("a@b.c", session.getMetadata().get("email"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertEquals("POST", request.method());
        assertTrue(request.headers().firstValue("Authorization").orElse("").startsWith("Bearer sk_test"));
        assertEquals("idem-1", request.headers().firstValue("Idempotency-Key").orElse(null));
    }

    @Test
    void retrieveSessionParsesExpandedPaymentIntent() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        """
                        {
                          "id":"cs_test_2",
                          "status":"complete",
                          "payment_status":"paid",
                          "payment_intent":{"id":"pi_expanded"},
                          "amount_total":2000,
                          "metadata":{}
                        }
                        """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StripeCheckoutSession session = new StripeCheckoutClient(http).retrieveSession("sk_test", "cs_test_2");
        assertEquals("pi_expanded", session.getPaymentIntent());
        assertEquals("paid", session.getPaymentStatus());
    }

    @Test
    void retrieveSessionThrowsStripeExceptionOnApiError() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("{\"error\":{\"message\":\"Invalid API Key\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StripeException ex = assertThrows(
                StripeException.class, () -> new StripeCheckoutClient(http).retrieveSession("bad", "cs_x"));
        assertEquals(401, ex.getStatusCode());
        assertEquals("Invalid API Key", ex.getMessage());
    }

    @Test
    void listSessionsReturnsHasMore() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body())
                .thenReturn(
                        """
                        {
                          "object":"list",
                          "has_more":true,
                          "data":[{"id":"cs_a","status":"open","payment_status":"unpaid","amount_total":100}]
                        }
                        """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        StripeCheckoutClient.ListResult list = new StripeCheckoutClient(http).listSessions("sk_test", 100, null);
        assertTrue(list.hasMore());
        assertEquals(1, list.data().size());
        assertEquals("cs_a", list.data().get(0).getId());
        assertNull(list.data().get(0).getPaymentIntent());
    }
}
