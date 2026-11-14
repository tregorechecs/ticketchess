package com.github.gcolin.event;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.event.Event;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.event.StatisticsReport;
import com.github.gcolin.event.StatisticsReportService;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import com.github.gcolin.platform.MustacheHtml;

@Path("event/{id:\\d+}/statistics")
@RequireRole(RoleCode.ADMIN)
public class EventStatisticsApi {

    @GET
    public MustacheHtml show(@PathParam("id") Integer eventId) {
        Event event = RequestContext.require().eventDao().find(eventId);
        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEvent(event);
        StatisticsReport report = AppContext.get().statisticsReportService().computeForEvent(event, subscriptions);
        return new MustacheHtml(report.toEventModel(event), "event/eventStatistics.mustache");
    }

    @GET
    @Path("csv")
    @Produces("text/csv; charset=UTF-8")
    public Response csv(@PathParam("id") Integer eventId) {
        Event event = RequestContext.require().eventDao().find(eventId);
        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEvent(event);
        String csv = AppContext.get().statisticsReportService().generateCsv(subscriptions, false);
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=statistiques-event-" + eventId + ".csv")
                .build();
    }

    @GET
    @Path("pdf")
    @Produces("application/pdf")
    public Response pdf(@PathParam("id") Integer eventId) {
        Event event = RequestContext.require().eventDao().find(eventId);
        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEvent(event);
        StatisticsReport report = AppContext.get().statisticsReportService().computeForEvent(event, subscriptions);
        try {
            byte[] pdf = AppContext.get().statisticsReportService().generatePdf(event.getName(), report);
            return Response.ok(pdf)
                    .header("Content-Disposition", "attachment; filename=statistiques-event-" + eventId + ".pdf")
                    .build();
        } catch (RuntimeException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("payments/pdf")
    @Produces("application/pdf")
    public Response paymentsPdf(@PathParam("id") Integer eventId) {
        Event event = RequestContext.require().eventDao().find(eventId);
        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEvent(event);
        try {
            byte[] pdf = AppContext.get().eventPaymentsReportService().generateForEvent(event, subscriptions);
            return Response.ok(pdf)
                    .header("Content-Disposition", "attachment; filename=paiements-event-" + eventId + ".pdf")
                    .build();
        } catch (RuntimeException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
