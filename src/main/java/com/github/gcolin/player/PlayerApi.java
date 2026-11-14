package com.github.gcolin.player;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.auth.RoleCode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.json.JSONObject;

@Path("player")
public class PlayerApi {

    @GET
    @Path("{licence}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @RequireRole(RoleCode.ADMIN)
    public String getPlayers(@PathParam("licence") String licence) {
        try {
            Player p = AppContext.get().luceneDb().searchJoueur(licence);
            if (p == null) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
            return new JSONObject(p).toString();
        } catch (ParseException | IOException e) {
            throw new WebApplicationException(e);
        }
    }
}
