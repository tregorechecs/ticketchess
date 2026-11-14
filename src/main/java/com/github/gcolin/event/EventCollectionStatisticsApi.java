package com.github.gcolin.event;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.event.EventCollection;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.event.StatisticsReport;
import com.github.gcolin.event.StatisticsReportService;
import com.github.gcolin.event.EventCollectionDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import com.github.gcolin.platform.MustacheHtml;

@Path("eventcollection/{id:\\d+}/statistics")
@RequireRole(RoleCode.ARBITRE)
public class EventCollectionStatisticsApi {

    @GET
    public MustacheHtml show(@PathParam("id") Integer eventCollectionId) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(eventCollectionId);
        if (eventCollection == null) {
            throw new NotFoundException();
        }

        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEventCollection(eventCollectionId);
        StatisticsReport report = AppContext.get().statisticsReportService().computeForEventCollection(subscriptions);
        return new MustacheHtml(report.toEventCollectionModel(eventCollection), "event/eventCollectionStatistics.mustache");
    }

    @GET
    @Path("csv")
    @Produces("text/csv; charset=UTF-8")
    public Response csv(@PathParam("id") Integer eventCollectionId) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(eventCollectionId);
        if (eventCollection == null) {
            throw new NotFoundException();
        }

        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEventCollection(eventCollectionId);
        String csv = AppContext.get().statisticsReportService().generateCsv(subscriptions, true);
        return Response.ok(csv)
                .header(
                        "Content-Disposition",
                        "attachment; filename=statistiques-collection-" + eventCollectionId + ".csv")
                .build();
    }

    @GET
    @Path("pdf")
    @Produces("application/pdf")
    public Response pdf(@PathParam("id") Integer eventCollectionId) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(eventCollectionId);
        if (eventCollection == null) {
            throw new NotFoundException();
        }

        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEventCollection(eventCollectionId);
        StatisticsReport report = AppContext.get().statisticsReportService().computeForEventCollection(subscriptions);
        try {
            byte[] pdf = AppContext.get().statisticsReportService().generatePdf(eventCollection.getName(), report);
            return Response.ok(pdf)
                    .header(
                            "Content-Disposition",
                            "attachment; filename=statistiques-collection-" + eventCollectionId + ".pdf")
                    .build();
        } catch (RuntimeException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("payments/pdf")
    @Produces("application/pdf")
    public Response paymentsPdf(@PathParam("id") Integer eventCollectionId) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(eventCollectionId);
        if (eventCollection == null) {
            throw new NotFoundException();
        }

        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEventCollection(eventCollectionId);
        try {
            byte[] pdf = AppContext.get().eventPaymentsReportService().generateForEventCollection(eventCollection, subscriptions);
            return Response.ok(pdf)
                    .header(
                            "Content-Disposition",
                            "attachment; filename=paiements-collection-" + eventCollectionId + ".pdf")
                    .build();
        } catch (RuntimeException e) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
