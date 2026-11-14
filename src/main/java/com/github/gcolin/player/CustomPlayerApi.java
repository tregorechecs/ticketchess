package com.github.gcolin.player;

import com.github.gcolin.auth.LoggedOnly;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.player.CustomPlayer;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.platform.PagedList;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.player.Find;
import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.registration.RegisterService;
import com.github.gcolin.player.CustomPlayerDao;
import com.github.gcolin.event.EventDao;
import jakarta.servlet.ServletException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import com.github.gcolin.platform.MustacheHtml;

@Path("customplayer")
public class CustomPlayerApi {

    @GET
    @RequireRole(RoleCode.ARBITRE)
    public MustacheHtml players(
            @QueryParam("page") @DefaultValue("1") Integer page,
            @QueryParam("size") @DefaultValue("25") Integer size) {
        if (page < 1) {
            throw new WebApplicationException("page must be >= 1", Response.Status.BAD_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new WebApplicationException("size must be between 1 and 100", Response.Status.BAD_REQUEST);
        }
        int start = (page - 1) * size;

        PagedList<CustomPlayer> paged = RequestContext.require().customPlayerDao().pageSorted(start, size);
        long totalItems = paged.getTotal();
        int totalPages = (int) Math.max(1, (totalItems + size - 1) / size);
        if (page > totalPages) {
            page = totalPages;
            start = (page - 1) * size;
            paged = RequestContext.require().customPlayerDao().pageSorted(start, size);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("players", paged.getElements());
        model.put("currentPage", page);
        model.put("pageSize", size);
        model.put("totalItems", totalItems);
        model.put("totalPages", totalPages);
        model.put("hasPrev", page > 1);
        model.put("hasNext", page < totalPages);
        model.put("prevPage", Math.max(1, page - 1));
        model.put("nextPage", Math.min(totalPages, page + 1));
        model.put("startIndex", start);
        return new MustacheHtml(model, "player/players.mustache");
    }

    @GET
    @LoggedOnly
    @Path("new")
    public MustacheHtml newplayer(
            @QueryParam("eventId") Integer eventId)
            throws ServletException, IOException {
        return edit(null, eventId);
    }

    @GET
    @LoggedOnly
    @Path("{id:\\d+}")
    public MustacheHtml edit(
            @PathParam("id") Integer id,
            @QueryParam("eventId") Integer eventId)
            throws ServletException, IOException {
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("eventId", eventId);
        model.put("eventIdValue", eventId != null ? String.valueOf(eventId) : "");

        CustomPlayer player;
        if (id != null) {
            player = RequestContext.require().customPlayerDao().find(id);
        } else {
            player = new CustomPlayer();
            player.setLicence("");
            player.setName("");
            player.setFirstname("");
            player.setBirthDate("");
            player.setElo("");
            player.setCreationUser("");
        }
        model.put("player", player);
        model.put("playerIdValue", player.getId() != null ? String.valueOf(player.getId()) : "");
        model.put("genderMaleSelected", Boolean.TRUE.equals(player.getGender()));
        model.put("genderFemaleSelected", Boolean.FALSE.equals(player.getGender()));
        model.put("canManageEvents", RequestContext.require().loggedUser() != null && RequestContext.require().loggedUser().canManageEvents());
        return new MustacheHtml(model, "player/customplayer.mustache");
    }

    @POST
    @LoggedOnly
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response save(
            @FormParam("id") Integer id,
            @FormParam("eventId") Integer eventId,
            @FormParam("licence") String licence,
            @FormParam("name") String name,
            @FormParam("birthdate") String birthdate,
            @FormParam("firstname") String firstname,
            @FormParam("gender") String gender,
            @FormParam("elo") String elo) {

        if (id == null && eventId != null) {
            String trimmedLicence = trim(licence);
            if (trimmedLicence != null) {
                var existingPlayer = RequestContext.require().find().player(trimmedLicence, null);
                if (existingPlayer != null && !existingPlayer.isEditable()) {
                    PlayerSubscription sub = RequestContext.require().registerService().registerPlayerToEvent(
                            RequestContext.require().eventDao().find(eventId), existingPlayer, RequestContext.require().loggedUser().getEmail());
                    return redirectAfterRegister(eventId, sub);
                }
            }
        }

        CustomPlayer player = new CustomPlayer();
        player.setLicence(licence);
        player.setName(name);
        player.setBirthDate(birthdate);
        player.setFirstname(firstname);
        player.setGender("true".equals(gender));
        player.setElo(elo);

        if (id != null) {
            player.setId(id);
            if (RequestContext.require().loggedUser().hasRole(RoleCode.ARBITRE)) {
                player.setCreationUser(RequestContext.require().loggedUser().getEmail());
            }
        } else {
            player.setCreationUser(RequestContext.require().loggedUser().getEmail());
        }
        AppContext.get().caches().getDebtCache().invalidateAll();
        if (id == null) {
            RequestContext.require().customPlayerDao().persist(player);
            RequestContext.require().customPlayerDao().flush();
        } else {
            RequestContext.require().customPlayerDao().merge(player);
        }

        if (eventId != null) {
            PlayerSubscription sub =
                    RequestContext.require().registerService().registerPlayerToEvent(RequestContext.require().eventDao().find(eventId), player, RequestContext.require().loggedUser().getEmail());
            return redirectAfterRegister(eventId, sub);
        }

        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                .path("event")
                .path("my")
                .queryParam("success", "register")
                .build();
        return Response.seeOther(uri).build();
    }

    private Response redirectAfterRegister(Integer eventId, PlayerSubscription sub) {
        if (sub == null) {
            URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                    .path("event")
                    .path("my")
                    .queryParam("success", "pending")
                    .build();
            return Response.seeOther(uri).build();
        }
        if (sub.getStatus() == PlayerSubscriptionStatus.PAID) {
            URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                    .path("event")
                    .path(eventId.toString())
                    .queryParam("success", "register")
                    .build();
            return Response.seeOther(uri).build();
        }
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                .path("event")
                .path("my")
                .queryParam("success", "register")
                .build();
        return Response.seeOther(uri).build();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
