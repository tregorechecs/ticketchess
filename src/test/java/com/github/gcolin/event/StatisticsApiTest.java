package com.github.gcolin.event;

import com.github.gcolin.platform.ApiTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.event.Event;
import com.github.gcolin.player.Find;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;

class StatisticsApiTest extends ApiTestBase {

    @Test
    void pageShouldBuildModelFromDaos() throws Exception {
        StatisticsApi api = new StatisticsApi();

        EventDao eventDao = mock(EventDao.class);
        PaymentDao paymentDao = mock(PaymentDao.class);
        PlayerSubscriptionDao playerSubscriptionDao = mock(PlayerSubscriptionDao.class);
        Find find = mock(Find.class);

        List<Event> closestEvents = List.of(new Event(), new Event());
        List<Object[]> topEvents = new ArrayList<>();
        topEvents.add(new Object[] {new Event(), 5L});

        when(eventDao.findClosestEvents(eq(10), any(SeasonScope.class))).thenReturn(closestEvents);
        when(eventDao.findTopEventsByParticipants(eq(10), any(SeasonScope.class))).thenReturn(topEvents);
        when(paymentDao.sumAllPayments(any(SeasonScope.class))).thenReturn(12345L);
        when(playerSubscriptionDao.findNotPaidWithEvent(any(SeasonScope.class))).thenReturn(List.of());

        ClubSeasonFilter clubSeasonFilter = mockClubSeasonFilter();

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPaymentDao(paymentDao);
            ctx.setPlayerSubscriptionDao(playerSubscriptionDao);
            ctx.setFind(find);
            ctx.setClubSeasonFilter(clubSeasonFilter);
        })) {

            MustacheHtml html = api.page(null);
            Map<String, Object> model = html.getModel();

            assertEquals("event/statistics.mustache", html.getTemplate());
            assertSame(closestEvents, model.get("closestEvents"));
            assertSame(topEvents, model.get("topEvents"));
            assertEquals(12345L, (Long) model.get("totalPayments"));
            assertEquals(0L, (Long) model.get("unpaidPlayerSubscriptions"));
        }
    }

    @Test
    void pageShouldDefaultTotalPaymentsToZeroWhenEmpty() throws Exception {
        StatisticsApi api = new StatisticsApi();

        EventDao eventDao = mock(EventDao.class);
        PaymentDao paymentDao = mock(PaymentDao.class);
        PlayerSubscriptionDao playerSubscriptionDao = mock(PlayerSubscriptionDao.class);
        Find find = mock(Find.class);

        when(eventDao.findClosestEvents(eq(10), any(SeasonScope.class))).thenReturn(List.of());
        when(eventDao.findTopEventsByParticipants(eq(10), any(SeasonScope.class))).thenReturn(List.of());
        when(paymentDao.sumAllPayments(any(SeasonScope.class))).thenReturn(0L);
        when(playerSubscriptionDao.findNotPaidWithEvent(any(SeasonScope.class))).thenReturn(List.of());

        ClubSeasonFilter clubSeasonFilter = mockClubSeasonFilter();

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPaymentDao(paymentDao);
            ctx.setPlayerSubscriptionDao(playerSubscriptionDao);
            ctx.setFind(find);
            ctx.setClubSeasonFilter(clubSeasonFilter);
        })) {

            MustacheHtml html = api.page(null);
            Map<String, Object> model = html.getModel();

            assertEquals(0L, (Long) model.get("totalPayments"));
            assertEquals(0L, (Long) model.get("unpaidPlayerSubscriptions"));
            assertTrue(((List<?>) model.get("closestEvents")).isEmpty());
            assertTrue(((List<?>) model.get("topEvents")).isEmpty());
        }
    }

}
