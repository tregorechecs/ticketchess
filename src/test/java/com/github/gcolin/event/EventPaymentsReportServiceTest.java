package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.payment.Payment;
import com.github.gcolin.payment.PaymentStatus;
import com.github.gcolin.payment.PaymentType;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.player.Find;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.platform.TestContext;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EventPaymentsReportServiceTest {

    @Test
    void generateForEventShouldProducePdfWithPaidSubscriptionsOnly() throws Exception {
        EventPaymentsReportService service = new EventPaymentsReportService();
        Find find = mock(Find.class);
        service.setProperties(new Properties());

        Event event = new Event();
        event.setId(42);
        event.setName("Open Test");
        event.setStartDate(LocalDateTime.of(2026, 3, 1, 9, 0));
        event.setEndDate(LocalDateTime.of(2026, 3, 1, 18, 0));

        IPlayer player = mock(IPlayer.class);
        when(player.getFirstname()).thenReturn("Alice");
        when(player.getName()).thenReturn("Dupont");
        when(find.player("A12345", null)).thenReturn(player);

        Payment payment = new Payment();
        payment.setType(PaymentType.CARD);
        payment.setUserEmail("alice@example.com");
        payment.setUpdatedAt(LocalDateTime.of(2026, 2, 15, 10, 0));

        PlayerSubscription paid = new PlayerSubscription();
        paid.setStatus(PlayerSubscriptionStatus.PAID);
        paid.setNrFfe("A12345");
        paid.setAmountCents(2500L);
        paid.setPayment(payment);
        paid.setEvent(event);

        PlayerSubscription unpaid = new PlayerSubscription();
        unpaid.setStatus(PlayerSubscriptionStatus.NOT_PAID);
        unpaid.setNrFfe("B99999");
        unpaid.setAmountCents(2500L);

        try (TestContext ignored = TestContext.open(find)) {
        byte[] pdf = service.generateForEvent(event, List.of(paid, unpaid));
        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, Math.min(8, pdf.length)).startsWith("%PDF"));
        }
    }

    @Test
    void resolvePaymentTypeShouldInferCardForStripeWithoutType() {
        Payment payment = new Payment();
        payment.setStripeSessionId("cs_test_123");
        org.junit.jupiter.api.Assertions.assertEquals(
                "CARD", EventPaymentsReportService.resolvePaymentType(payment));
    }

    @Test
    void generateForAccountingShouldProducePdfFromPaidPayments() {
        EventPaymentsReportService service = new EventPaymentsReportService();
        service.setProperties(new Properties());

        Payment paid = new Payment();
        paid.setId(12L);
        paid.setStatus(PaymentStatus.PAID);
        paid.setType(PaymentType.BANK_TRANSFER);
        paid.setUserEmail("compta@example.com");
        paid.setAmount(42.5);
        paid.setUpdatedAt(LocalDateTime.of(2026, 8, 20, 10, 0));

        Payment pending = new Payment();
        pending.setId(13L);
        pending.setStatus(PaymentStatus.PENDING);
        pending.setAmount(100.0);

        byte[] pdf = service.generateForAccounting(List.of(paid, pending), SeasonScope.all());

        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, Math.min(8, pdf.length)).startsWith("%PDF"));
    }

    @Test
    void emptyCollectionAndAccountingDetailsWithNullsStillProducePdf() {
        EventPaymentsReportService service = new EventPaymentsReportService();
        service.setProperties(new Properties());
        EventCollection collection = new EventCollection();
        collection.setId(8);
        collection.setName("Empty Cup");
        assertTrue(service.generateForEventCollection(collection, List.of()).length > 100);
        assertTrue(service.generateForAccountingDetails(
                        List.of(new EventPaymentsReportService.AccountingDetailRow(
                                null, null, "UNKNOWN", null, 0.0, "other", null)),
                        SeasonScope.of(season()))
                .length
                > 100);
        assertEquals("UNKNOWN", EventPaymentsReportService.resolvePaymentType(null));
        Payment payment = new Payment();
        payment.setStripeIntent("intent");
        assertEquals("CARD", EventPaymentsReportService.resolvePaymentType(payment));
    }

    private static com.github.gcolin.club.ClubSeason season() {
        com.github.gcolin.club.ClubSeason season = new com.github.gcolin.club.ClubSeason();
        season.setId(2026);
        season.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        season.setEndDate(java.time.LocalDate.of(2026, 12, 31));
        return season;
    }
}
