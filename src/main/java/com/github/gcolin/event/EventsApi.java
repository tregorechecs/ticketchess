package com.github.gcolin.event;

import com.github.gcolin.platform.RequestContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;

@Path("events")
public class EventsApi {

    @GET
    public Response events() {
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").build();

        return Response.seeOther(uri).build();
    }

    @GET
    @Path("{category}")
    public Response events(@PathParam("category") String category) {
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path(category).build();

        return Response.seeOther(uri).build();
    }
}
