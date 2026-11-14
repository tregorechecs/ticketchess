package com.github.gcolin.platform;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

/**
 * Stores the request {@link jakarta.ws.rs.core.UriInfo} on the {@link RequestContext} so resources
 * and providers can read it without {@code @Context} injection (there is no HK2).
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class UriInfoCaptureFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        RequestContext ctx = RequestContext.current();
        if (ctx != null) {
            ctx.setUriInfo(requestContext.getUriInfo());
        }
    }
}
