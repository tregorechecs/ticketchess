package com.github.gcolin.event;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.event.EventCollection;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.player.Find;
import com.github.gcolin.event.EventCollectionDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.platform.BroadcastMail;
import com.github.gcolin.platform.MailTemplate;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.platform.Config;
import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.platform.AppContext;

@Path("eventcollection/{id:\\d+}/mail")
@RequireRole(RoleCode.ARBITRE)
public class EventCollectionMailApi {

    private static final Logger logger = LoggerFactory.getLogger(EventCollectionMailApi.class);

    @GET
    public MustacheHtml showForm(@PathParam("id") Integer id, @QueryParam("sent") Integer sent) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(id);
        if (eventCollection == null) {
            throw new NotFoundException();
        }
        Map<String, Object> model = new HashMap<>();
        model.put("eventCollection", eventCollection);
        if (sent != null) {
            model.put("sent", sent);
        }
        return new MustacheHtml(model, "event/eventcollectionMail.mustache");
    }

    @POST
    @Path("preview")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response preview(
            @PathParam("id") Integer id, @FormParam("body") String body, @FormParam("bcc") boolean bcc) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(id);
        if (eventCollection == null) {
            throw new NotFoundException();
        }
        Parser parser = Parser.builder().build();
        Node document = parser.parse(body == null ? "" : body);
        HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();

        BroadcastMail mail = new BroadcastMail();
        mail.setEventName(eventCollection.getName());
        mail.setName(bcc ? "" : "NOM Prénom");
        mail.setBody(renderer.render(document));

        try {
            AppContext.get().config().applyOrg(mail);
            MailTemplate tmpl = new MailTemplate();
            String html = tmpl.render(mail.getTemplate(), mail);
            return Response.ok(html, MediaType.TEXT_HTML).build();
        } catch (Exception e) {
            logger.error("Failed to render mail preview for eventCollection {}: {}", id, e.getMessage());
            return Response.serverError().build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response sendMails(
            @PathParam("id") Integer id,
            @FormParam("subject") String subject,
            @FormParam("body") String body,
            @FormParam("subscriptionStatus") String subscriptionStatus,
            @FormParam("minElo") Integer minElo,
            @FormParam("maxElo") Integer maxElo,
            @FormParam("sendAsBcc") String sendAsBcc) {
        EventCollection eventCollection = RequestContext.require().eventCollectionDao().find(id);
        if (eventCollection == null) {
            throw new NotFoundException();
        }
        List<PlayerSubscription> subscriptions = RequestContext.require().playerSubscriptionDao().findByEventCollection(id);

        int sent = "true".equals(sendAsBcc)
                ? sendBccMails(eventCollection, subscriptions, body, subject, subscriptionStatus, minElo, maxElo, id)
                : sendIndividualMails(
                        eventCollection, subscriptions, body, subject, subscriptionStatus, minElo, maxElo, id);

        URI location = RequestContext.require().uriInfo().getBaseUriBuilder()
                .path("eventcollection/{id}/mail")
                .queryParam("sent", sent)
                .build(id);
        return Response.seeOther(location).build();
    }

    private int sendBccMails(
            EventCollection eventCollection,
            List<PlayerSubscription> subscriptions,
            String body,
            String subject,
            String subscriptionStatus,
            Integer minElo,
            Integer maxElo,
            Integer collectionId) {
        List<String> bccAddresses = new ArrayList<>();
        for (PlayerSubscription sub : subscriptions) {
            if (statusMatches(sub, subscriptionStatus) && isValidCreationUser(sub) && eloMatches(sub, minElo, maxElo)) {
                bccAddresses.add(sub.getCreationUser());
            }
        }
        if (bccAddresses.isEmpty()) {
            return 0;
        }
        try {
            BroadcastMail mail = buildBroadcastMail(eventCollection, body, "", null);
            AppContext.get().sendMail().sendBcc(mail, bccAddresses, subject);
            return bccAddresses.size();
        } catch (Exception e) {
            logger.error("Failed to send BCC broadcast mail for eventCollection {}: {}", collectionId, e.getMessage());
            return 0;
        }
    }

    private int sendIndividualMails(
            EventCollection eventCollection,
            List<PlayerSubscription> subscriptions,
            String body,
            String subject,
            String subscriptionStatus,
            Integer minElo,
            Integer maxElo,
            Integer collectionId) {
        int sent = 0;
        for (PlayerSubscription sub : subscriptions) {
            if (statusMatches(sub, subscriptionStatus) && isValidCreationUser(sub) && eloMatches(sub, minElo, maxElo)) {
                try {
                    IPlayer player = RequestContext.require().find().player(sub.getNrFfe(), null);
                    BroadcastMail mail =
                            buildBroadcastMail(eventCollection, body, player != null ? player.getFullname() : "", sub.getCreationUser());
                    AppContext.get().sendMail().send(mail, sub.getCreationUser(), subject);
                    sent++;
                } catch (Exception e) {
                    logger.error(
                            "Failed to send broadcast mail for eventCollection {}: {}", collectionId, e.getMessage());
                }
            }
        }
        return sent;
    }

    private boolean eloMatches(PlayerSubscription sub, Integer minElo, Integer maxElo) {
        if (minElo == null && maxElo == null) {
            return true;
        }
        try {
            IPlayer player = RequestContext.require().find().player(sub.getNrFfe(), null);
            if (player == null) {
                return minElo == null;
            }
            String ratingStr = player.getRating();
            if (ratingStr == null || ratingStr.isEmpty()) {
                return minElo == null;
            }
            int elo = Integer.parseInt(ratingStr.replaceAll("[^0-9]", ""));
            if (minElo != null && elo < minElo) {
                return false;
            }
            if (maxElo != null && elo > maxElo) {
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.debug("Could not determine ELO for player {}: {}", sub.getNrFfe(), e.getMessage());
            return minElo == null;
        }
    }

    private boolean statusMatches(PlayerSubscription sub, String subscriptionStatus) {
        if (subscriptionStatus == null || subscriptionStatus.isEmpty()) {
            return sub.getStatus() != PlayerSubscriptionStatus.CANCELLED;
        }
        try {
            return sub.getStatus() == PlayerSubscriptionStatus.valueOf(subscriptionStatus);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidCreationUser(PlayerSubscription sub) {
        return sub.getCreationUser() != null && !sub.getCreationUser().isEmpty();
    }

    private BroadcastMail buildBroadcastMail(EventCollection eventCollection, String body, String playerName, String emailAddress) {
        BroadcastMail mail = new BroadcastMail();
        mail.setEventName(eventCollection.getName());
        mail.setName(playerName);
        Parser parser = Parser.builder().build();
        Node document = parser.parse(body);
        HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
        mail.setBody(renderer.render(document));
        
        if (emailAddress != null && !emailAddress.isBlank()) {
            String jwt = Jwts.builder()
                .subject(emailAddress.trim())
                .issuer(eventCollection.getName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30))
                .signWith(AppContext.get().config().getKeys(), Config.JWT_ALGORITHM)
                .compact();
            String loginUrl = RequestContext.require().uriInfo().getBaseUri() + "login?jwt=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8);
            mail.setLoginUrl(loginUrl);
        }
        return mail;
    }
}
