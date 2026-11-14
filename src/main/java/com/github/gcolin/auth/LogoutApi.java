package com.github.gcolin.auth;

import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Path("logout")
public class LogoutApi {

    @GET
    public Response logout() {
        var session = RequestContext.require().request().getSession(false);
        if (session != null) {
            AppContext.get().activeLoggedUsers().remove(session.getId());
            session.invalidate();
        }

        NewCookie cookie = new NewCookie.Builder("remember_me")
                .value("")
                .path("/")
                .maxAge(0)
                .build();

        String logoutUrl = AppContext.get().config().getOauthLogoutUrl();
        if (logoutUrl != null && !logoutUrl.isBlank()) {
            Claims claims = RequestContext.require().loggedUser().getClaims();
            if (claims != null) {
                Date nowPlus5Min = new Date(new Date().getTime() + 5 * 60 * 1000);
                if (claims.getIssuedAt().before(nowPlus5Min)) {
                    String id = claims.getId();
                    if (id != null) {
                        return Response.seeOther(URI.create(logoutUrl
                                        + "?id_token_hint="
                                        + id
                                        + "&post_logout_redirect_uri="
                                        + URLEncoder.encode(AppContext.get().config().getPublicBaseUrl(), StandardCharsets.UTF_8)))
                                .cookie(cookie)
                                .build();
                    }
                }
            }
        }
        return Response.seeOther(RequestContext.require().uriInfo().getBaseUri()).cookie(cookie).build();
    }
}
