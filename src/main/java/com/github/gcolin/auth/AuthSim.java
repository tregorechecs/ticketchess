package com.github.gcolin.auth;

import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Caches;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Properties;

@Path("auth-sim")
public class AuthSim {

    @GET
    @Path("logAs")
    @RequireRole(RoleCode.ADMIN)
    public Response logAs(@QueryParam("email") String email, @QueryParam("name") String name) {
        requireImpersonationAllowed();
        RequestContext.require().loggedUser().setEmail(email);
        RequestContext.require().loggedUser().setUsername(name);
        RequestContext.require().loggedUser().setLogged(true);
        AppContext.get().caches().getDebtCache().invalidateAll();
        AppContext.get().caches().getRoleCache().invalidateAll();
        rememberAuthInSession(email);

        String jwt = Jwts.builder()
                .subject(email)
                .issuer(name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30))
                .signWith(AppContext.get().config().getKeys(), Config.JWT_ALGORITHM)
                .compact();

        NewCookie newCookie = new NewCookie.Builder("remember_me")
                .httpOnly(true)
                .maxAge(60 * 60 * 24 * 30)
                .path("/")
                .value(jwt)
                .build();

        return Response.seeOther(RequestContext.require().uriInfo().getBaseUri()).cookie(newCookie).build();
    }

    @GET
    public Response doGet(@QueryParam("state") String state) throws ServletException, IOException {
        requireDevAuthSim();

        RequestContext.require().loggedUser().setEmail(AppContext.get().config().getProperties().getProperty("auth.USER_EMAIL"));
        RequestContext.require().loggedUser().setUsername(AppContext.get().config().getProperties().getProperty("auth.USER_NAME"));
        RequestContext.require().loggedUser().setLogged(true);
        AppContext.get().caches().getDebtCache().invalidateAll();
        AppContext.get().caches().getRoleCache().invalidateAll();
        rememberAuthInSession(RequestContext.require().loggedUser().getEmail());

        String jwt = Jwts.builder()
                .subject(RequestContext.require().loggedUser().getEmail())
                .issuer(RequestContext.require().loggedUser().getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30)) // 30 jours
                .signWith(AppContext.get().config().getKeys(), Config.JWT_ALGORITHM)
                .compact();

        NewCookie newCookie = new NewCookie.Builder("remember_me")
                .httpOnly(true)
                .maxAge(60 * 60 * 24 * 30)
                .path("/")
                .value(jwt)
                .build();

        if (state != null) {
            String decodedState = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            return Response.seeOther(
                            RequestContext.require().uriInfo().getBaseUriBuilder().path(decodedState).build())
                    .cookie(newCookie)
                    .build();
        } else {
            return Response.seeOther(RequestContext.require().uriInfo().getBaseUri()).cookie(newCookie).build();
        }
    }

    private void rememberAuthInSession(String email) {
        HttpServletRequest request = RequestContext.require().request();
        if (request != null) {
            request.getSession(true).setAttribute("auth.email", email);
        }
    }

    private void requireDevAuthSim() {
        if (AppContext.get().config().isOauthEnabled() || !AppContext.get().config().isAuthSimEnabled()) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }

    private void requireImpersonationAllowed() {
        if (!AppContext.get().config().isOauthEnabled() && !AppContext.get().config().isTestMode()) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
    }
}
