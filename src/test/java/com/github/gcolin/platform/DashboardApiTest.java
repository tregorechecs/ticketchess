package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.player.Find;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardApiTest extends ApiTestBase {

    @Test
    void pageShouldBuildDashboardWithSubscriptions() throws Exception {
        DashboardApi api = new DashboardApi();
        PlayerSubscriptionDao dao = mock(PlayerSubscriptionDao.class);
        Find find = mock(Find.class);

        PlayerSubscription sub = new PlayerSubscription();
        sub.setNrFfe("FFE123");

        when(dao.findWithoutPaymentWithEvent(eq(PlayerSubscriptionStatus.PAID), any(SeasonScope.class)))
                .thenReturn(List.of(sub));
        when(dao.findCancelledWithEvent(any(SeasonScope.class))).thenReturn(List.of());
        when(dao.findNotPaidWithEvent(any(SeasonScope.class))).thenReturn(List.of());

        ClubSeasonFilter clubSeasonFilter = mockClubSeasonFilter();

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setPlayerSubscriptionDao(dao);
            ctx.setFind(find);
            ctx.setClubSeasonFilter(clubSeasonFilter);
        })) {

            MustacheHtml html = api.page(null);

            assertEquals("platform/dashboard.mustache", html.getTemplate());
            Map<String, Object> model = html.getModel();
            assertEquals(1, ((List<?>) model.get("subsWithoutPayment")).size());
            assertEquals(0, ((List<?>) model.get("cancelledSubs")).size());
            assertEquals(0, ((List<?>) model.get("notPaidSubs")).size());
        }
    }

    @Test
    void pageWithMultipleSubscriptionsShouldReturnAllLists() throws Exception {
        DashboardApi api = new DashboardApi();
        PlayerSubscriptionDao dao = mock(PlayerSubscriptionDao.class);
        Find find = mock(Find.class);

        PlayerSubscription sub1 = new PlayerSubscription();
        sub1.setNrFfe("FFE1");

        PlayerSubscription sub2 = new PlayerSubscription();
        sub2.setNrFfe("FFE2");

        PlayerSubscription sub3 = new PlayerSubscription();
        sub3.setNrFfe("FFE3");

        when(dao.findWithoutPaymentWithEvent(eq(PlayerSubscriptionStatus.PAID), any(SeasonScope.class)))
                .thenReturn(List.of(sub1));
        when(dao.findCancelledWithEvent(any(SeasonScope.class))).thenReturn(List.of(sub2));
        when(dao.findNotPaidWithEvent(any(SeasonScope.class))).thenReturn(List.of(sub3));

        ClubSeasonFilter clubSeasonFilter = mockClubSeasonFilter();

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setPlayerSubscriptionDao(dao);
            ctx.setFind(find);
            ctx.setClubSeasonFilter(clubSeasonFilter);
        })) {

            MustacheHtml html = api.page(null);

            Map<String, Object> model = html.getModel();
            assertEquals(1, ((List<?>) model.get("subsWithoutPayment")).size());
            assertEquals(1, ((List<?>) model.get("cancelledSubs")).size());
            assertEquals(1, ((List<?>) model.get("notPaidSubs")).size());
        }
    }

}
