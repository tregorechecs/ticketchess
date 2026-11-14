package com.github.gcolin.event;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.platform.MustacheHtml;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequireRole(RoleCode.ARBITRE)
@Path("admin/events")
public class EventAdminApi {

    private final EventAdminTreeBuilder treeBuilder = new EventAdminTreeBuilder();

    @GET
    public MustacheHtml page(@QueryParam("seasonId") Integer seasonId) {
        SeasonScope scope = RequestContext.require().clubSeasonFilter().resolve(seasonId);
        List<Event> events = RequestContext.require().eventDao().findAllForAdmin(scope);
        RequestContext.require().eventDao().fillNbSubscriptions(events);
        RequestContext.require().eventDao().detachAll(events);

        Map<String, Object> model = new HashMap<>();
        model.put("groups", treeBuilder.build(RequestContext.require().eventGroupDao().all(), events));
        RequestContext.require().clubSeasonFilter().addToModel(model, seasonId);
        return new MustacheHtml(model, "event/eventAdminTree.mustache");
    }
}
