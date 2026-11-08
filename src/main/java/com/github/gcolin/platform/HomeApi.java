package com.github.gcolin.platform;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;

@Path("/")
public class HomeApi {

    @Inject
    private Config config;

    @Context
    UriInfo uriInfo;

    @GET
    public Response home() {
        Page page = config != null ? config.pageForRequest() : null;
        String target = "event";
        if (page != null) {
            if (page.isEventsEnabled()) {
                target = "event";
            } else if (page.isMembershipsEnabled()) {
                target = "club-register";
            } else if (page.isPaymentsEnabled()) {
                target = "payment";
            } else {
                target = "admin";
            }
        }
        URI uri = uriInfo.getBaseUriBuilder().path(target).build();
        return Response.seeOther(uri).build();
    }
}
