package com.github.gcolin.event;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.event.Event;
import com.github.gcolin.platform.Transactional;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.player.Find;
import com.github.gcolin.platform.ServiceUtils;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.gcolin.platform.MustacheHtml;

@Path("statistics")
public class StatisticsApi {

    @Inject
    private EventDao eventDao;

    @Inject
    private PaymentDao paymentDao;

    @Inject
    private PlayerSubscriptionDao playerSubscriptionDao;

    @Inject
    private Find find;

    @Inject
    private ClubSeasonFilter clubSeasonFilter;

    @GET
    @RequireRole(value = RoleCode.TRESORIER, or = RoleCode.ARBITRE)
    @Transactional
    public MustacheHtml page(@QueryParam("seasonId") Integer seasonId) {
        Map<String, Object> model = new HashMap<>();
        SeasonScope scope = clubSeasonFilter.resolve(seasonId);
        clubSeasonFilter.addToModel(model, seasonId);

        List<Event> closestEvents = eventDao.findClosestEvents(10, scope);
        model.put("closestEvents", closestEvents);

        List<Object[]> topEvents = eventDao.findTopEventsByParticipants(10, scope);
        model.put("topEvents", topEvents);

        model.put("totalPayments", paymentDao.sumAllPayments(scope));
        model.put("unpaidPlayerSubscriptions", calculateUnpaidSubscriptionsTotal(scope));

        return new MustacheHtml(model, "event/statistics.mustache");
    }

    private long calculateUnpaidSubscriptionsTotal(SeasonScope scope) {
        List<PlayerSubscription> unpaidSubscriptions = playerSubscriptionDao.findNotPaidWithEvent(scope);
        long totalCents = 0;

        for (PlayerSubscription subscription : unpaidSubscriptions) {
            if (subscription.getNrFfe() != null && subscription.getEvent() != null) {
                try {
                    IPlayer player = find.player(subscription.getNrFfe(), subscription.getEvent().getEventType());
                    if (player != null) {
                        totalCents += ServiceUtils.calculatePrice(player, subscription.getEvent());
                    }
                } catch (Exception e) {
                    // Ignore if player not found
                }
            }
        }

        return totalCents;
    }
}
