package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventCollectionStatisticsApiTest {
    @Test
    void showCsvPdfAndPaymentsPdfUseCollectionData() throws Exception {
        EventCollectionStatisticsApi api = new EventCollectionStatisticsApi();
        EventCollectionDao collections = mock(EventCollectionDao.class);
        PlayerSubscriptionDao subscriptions = mock(PlayerSubscriptionDao.class);
        StatisticsReportService statistics = mock(StatisticsReportService.class);
        EventPaymentsReportService payments = mock(EventPaymentsReportService.class);
        EventCollection collection = new EventCollection();
        collection.setId(7);
        collection.setName("Cup");
        List<PlayerSubscription> rows = List.of(new PlayerSubscription());
        StatisticsReport report = mock(StatisticsReport.class);
        when(collections.find(7)).thenReturn(collection);
        when(subscriptions.findByEventCollection(7)).thenReturn(rows);
        when(statistics.computeForEventCollection(rows)).thenReturn(report);
        when(report.toEventCollectionModel(collection)).thenReturn(java.util.Map.of("ok", true));
        when(statistics.generateCsv(rows, true)).thenReturn("header\n");
        when(statistics.generatePdf("Cup", report)).thenReturn(new byte[] {1, 2});
        when(payments.generateForEventCollection(collection, rows)).thenReturn(new byte[] {3, 4});
        inject(api, "eventCollectionDao", collections);
        inject(api, "playerSubscriptionDao", subscriptions);
        inject(api, "statisticsReportService", statistics);
        inject(api, "eventPaymentsReportService", payments);

        MustacheHtml html = api.show(7);
        assertEquals("event/eventCollectionStatistics.mustache", html.getTemplate());
        assertEquals("header\n", api.csv(7).getEntity());
        assertEquals(200, api.pdf(7).getStatus());
        assertEquals(200, api.paymentsPdf(7).getStatus());
        verify(statistics).generatePdf("Cup", report);
        verify(payments).generateForEventCollection(collection, rows);
    }

    @Test
    void missingCollectionIsNotFoundForEveryEndpoint() throws Exception {
        EventCollectionStatisticsApi api = new EventCollectionStatisticsApi();
        EventCollectionDao dao = mock(EventCollectionDao.class);
        when(dao.find(99)).thenReturn(null);
        inject(api, "eventCollectionDao", dao);
        assertThrows(NotFoundException.class, () -> api.show(99));
        assertThrows(NotFoundException.class, () -> api.csv(99));
        assertThrows(NotFoundException.class, () -> api.pdf(99));
        assertThrows(NotFoundException.class, () -> api.paymentsPdf(99));
    }

    @Test
    void reportFailuresBecomeInternalServerError() throws Exception {
        EventCollectionStatisticsApi api = new EventCollectionStatisticsApi();
        EventCollectionDao collections = mock(EventCollectionDao.class);
        PlayerSubscriptionDao subscriptions = mock(PlayerSubscriptionDao.class);
        StatisticsReportService statistics = mock(StatisticsReportService.class);
        EventPaymentsReportService payments = mock(EventPaymentsReportService.class);
        EventCollection collection = new EventCollection();
        collection.setName("Cup");
        when(collections.find(1)).thenReturn(collection);
        when(subscriptions.findByEventCollection(1)).thenReturn(List.of());
        when(statistics.computeForEventCollection(anyList())).thenReturn(mock(StatisticsReport.class));
        when(statistics.generatePdf(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("pdf"));
        when(payments.generateForEventCollection(org.mockito.ArgumentMatchers.any(), anyList()))
                .thenThrow(new RuntimeException("payments"));
        inject(api, "eventCollectionDao", collections);
        inject(api, "playerSubscriptionDao", subscriptions);
        inject(api, "statisticsReportService", statistics);
        inject(api, "eventPaymentsReportService", payments);
        assertEquals(500, assertThrows(WebApplicationException.class, () -> api.pdf(1)).getResponse().getStatus());
        assertEquals(
                500, assertThrows(WebApplicationException.class, () -> api.paymentsPdf(1)).getResponse().getStatus());
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
