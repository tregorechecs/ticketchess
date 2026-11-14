package com.github.gcolin.event;

import com.github.gcolin.platform.RequestContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;

@Path("myevents")
public class MyEventsApi {

    @GET
    public Response events() {
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path("my").build();

        return Response.seeOther(uri).build();
    }
}
