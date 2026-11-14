package com.github.gcolin.event;

import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.auth.LoggedOnly;
import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.player.CustomPlayer;
import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventCollectionOptionType;
import com.github.gcolin.event.EventOption;
import com.github.gcolin.event.EventOptionType;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.registration.PlayerPendingSubscription;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.platform.Transactional;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.event.EventStatus;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.desk.EventDeskHub;
import com.github.gcolin.desk.EventDeskService;
import com.github.gcolin.event.EventGroupFilter;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.player.Find;
import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.registration.RegisterService;
import com.github.gcolin.platform.ServiceUtils;
import com.github.gcolin.event.EventCollectionDao;
import com.github.gcolin.event.EventCollectionOptionDao;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.event.EventGroupDao;
import com.github.gcolin.event.EventOptionDao;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.registration.PlayerPendingSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.event.EventCache;
import com.github.gcolin.event.EventsCache;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.player.Player;

@Path("event")
public class EventApi {

    private static final Logger logger = LoggerFactory.getLogger(EventApi.class);

    @GET
    public MustacheHtml events(
            @QueryParam("status") @DefaultValue("ACTIVE") String status,
            @QueryParam("seasonId") Integer seasonId)
            throws IOException {
        return events(status, null, seasonId);
    }

    @GET
    @LoggedOnly
    @Path("my")
    public MustacheHtml myevents(
            @QueryParam("success") String success,
            @QueryParam("display") String display)
            throws IOException {

        List<PlayerSubscription> subs = RequestContext.require().playerSubscriptionDao().findByCreationUserWithEvents(RequestContext.require().loggedUser().getEmail());
        List<PlayerPendingSubscription> pendingSubs =
            RequestContext.require().playerPendingSubscriptionDao().findByCreationUserWithEvent(RequestContext.require().loggedUser().getEmail());

        List<Event> events = new ArrayList<Event>();
        for (PlayerSubscription s : subs) {
            Event event = null;
            for (Event evt : events) {
                if (evt.getId().equals(s.getEvent().getId())) {
                    event = evt;
                    break;
                }
            }
            if (event == null) {
                events.add(s.getEvent());
                event = s.getEvent();
                event.setPlayers(new ArrayList<DisplayPlayer>());
                RequestContext.require().eventDao().fillSubscriptionLimits(event);
            }
            IPlayer p = RequestContext.require().find().player(s.getNrFfe(), null);
            if (p == null) {
                logger.warn(
                        "cannot find player {} subscribed to event {}",
                        s.getNrFfe(),
                        s.getEvent().getName());
                continue;
            }
            DisplayPlayer player = new DisplayPlayer(p);
            if (s.getStatus() == PlayerSubscriptionStatus.NOT_PAID) {
                player.setPriceCents(ServiceUtils.calculatePrice(p, event));
            } else if (s.getStatus() == PlayerSubscriptionStatus.PAID && s.getAmountCents() != null) {
                player.setPriceCents(s.getAmountCents());
            }
            player.setRating(p, event.getEventType());
            if (p.isEditable()
                    && (RequestContext.require().loggedUser().hasRole(RoleCode.ARBITRE)
                            || (p instanceof CustomPlayer
                                    && RequestContext.require().loggedUser().getEmail().equals(((CustomPlayer) p).getCreationUser())))) {
                player.setEditable(true);
            }
            player.setStatus(s.getStatus());
            player.setSubId(s.getId());
            player.setAttendanceAt(s.getAttendanceAt());
            event.getPlayers().add(player);
        }
        boolean showFinished = "all".equalsIgnoreCase(display);
        LocalDateTime now = LocalDateTime.now();
        boolean hasFinishedEvents = events.stream()
                .anyMatch(event -> event.getEndDate() != null && event.getEndDate().plusDays(1).isBefore(now));
        if (!showFinished) {
            events.removeIf(event -> event.getEndDate() != null && event.getEndDate().plusDays(1).isBefore(now));
        }

        // Group events by their collection while keeping the original start date order.
        events.sort(Comparator.comparing(Event::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        List<Event> pendingEvents = new ArrayList<Event>();
        Map<Integer, Boolean> collectionQueueEnabledById = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> pendingPositionsByEventId = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> pendingPositionsByCollectionId = new HashMap<>();
        for (PlayerPendingSubscription pending : pendingSubs) {
            Event event = null;
            for (Event evt : pendingEvents) {
                if (evt.getId().equals(pending.getEvent().getId())) {
                    event = evt;
                    break;
                }
            }
            if (event == null) {
                Event sourceEvent = pending.getEvent();
                Event pendingEventView = new Event();
                pendingEventView.setId(sourceEvent.getId());
                pendingEventView.setName(sourceEvent.getName());
                pendingEventView.setStartDate(sourceEvent.getStartDate());
                pendingEventView.setEndDate(sourceEvent.getEndDate());
                pendingEventView.setEventType(sourceEvent.getEventType());
                pendingEventView.setEventCollection(sourceEvent.getEventCollection());
                pendingEvents.add(pendingEventView);
                event = pendingEventView;
                event.setPlayers(new ArrayList<DisplayPlayer>());
            }

            IPlayer p = RequestContext.require().find().player(pending.getNrFfe(), null);
            if (p == null) {
                logger.warn(
                        "cannot find player {} pending for event {}",
                        pending.getNrFfe(),
                        pending.getEvent().getName());
                continue;
            }

            DisplayPlayer player = new DisplayPlayer(p);
            player.setRating(p, event.getEventType());
            Integer ahead = resolvePendingQueueAhead(
                    pending,
                    collectionQueueEnabledById,
                    pendingPositionsByEventId,
                    pendingPositionsByCollectionId) - AppContext.get().config().getPendingQueueOffset();
            if(ahead < 0) {
                ahead = 0;
            }
            player.setPendingQueueAhead(ahead);
            if (p.isEditable()
                    && (RequestContext.require().loggedUser().hasRole(RoleCode.ARBITRE)
                            || (p instanceof CustomPlayer
                                    && RequestContext.require().loggedUser().getEmail().equals(((CustomPlayer) p).getCreationUser())))) {
                player.setEditable(true);
            }
            event.getPlayers().add(player);
        }

        hasFinishedEvents = hasFinishedEvents
                || pendingEvents.stream()
                        .anyMatch(event -> event.getEndDate() != null && event.getEndDate().plusDays(1).isBefore(now));
        if (!showFinished) {
            pendingEvents.removeIf(event -> event.getEndDate() != null && event.getEndDate().plusDays(1).isBefore(now));
        }

        pendingEvents.sort(
                Comparator.comparing(Event::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        // Build toggle URLs
        var toggleDisplayUrlBuilder = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path("my");
        if (!showFinished) {
        	toggleDisplayUrlBuilder.queryParam("display", "all");
        }
        String toggleDisplayUrl = toggleDisplayUrlBuilder.build().toString();

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("events", events);
        model.put("pendingEvents", pendingEvents);
        model.put("showFinished", showFinished);
        model.put("hasFinishedEvents", hasFinishedEvents);
        model.put("toggleDisplayUrl", toggleDisplayUrl);
        model.put("stripePublic", Config.getStripePublicKey(AppContext.get().config().getProperties()));
        model.put("stripeSimulated", Config.isStripeSimulated(AppContext.get().config().getProperties()));
        model.put("stripeCardEventsEnabled", Config.isStripeCardEnabledForEvents(AppContext.get().config().getProperties()));
        model.put("bankTransferEventsEnabled", Config.isBankTransferEnabledForEvents(AppContext.get().config().getProperties()));
        model.put("success", success);
        model.put("paidPayments", RequestContext.require().paymentDao().findAllPaidNotFreeByUser(RequestContext.require().loggedUser().getEmail()));

        return new MustacheHtml(model, "event/myevents.mustache");
    }

    private Integer resolvePendingQueueAhead(
            PlayerPendingSubscription pending,
            Map<Integer, Boolean> collectionQueueEnabledById,
            Map<Integer, Map<Integer, Integer>> pendingPositionsByEventId,
            Map<Integer, Map<Integer, Integer>> pendingPositionsByCollectionId) {
        Event pendingEvent = pending.getEvent();
        if (pendingEvent == null || pendingEvent.getId() == null) {
            return 0;
        }

        if (useEventCollectionPendingQueue(pendingEvent, collectionQueueEnabledById)) {
            Integer collectionId = pendingEvent.getEventCollection().getId();
            Map<Integer, Integer> pendingPositions = pendingPositionsByCollectionId.computeIfAbsent(
                    collectionId,
                    id -> buildPendingPositionsById(RequestContext.require().playerPendingSubscriptionDao().findByEventCollection(id)));
            return pending.getId() == null ? 0 : pendingPositions.getOrDefault(pending.getId(), 0);
        }

        Map<Integer, Integer> pendingPositions = pendingPositionsByEventId.computeIfAbsent(
                pendingEvent.getId(),
                id -> buildPendingPositionsById(RequestContext.require().playerPendingSubscriptionDao().findByEvent(pendingEvent)));
        return pending.getId() == null ? 0 : pendingPositions.getOrDefault(pending.getId(), 0);
    }

    private Map<Integer, Integer> buildPendingPositionsById(List<PlayerPendingSubscription> pendingSubscriptions) {
        Map<Integer, Integer> positionsById = new HashMap<>();
        int index = 0;
        for (PlayerPendingSubscription pendingSubscription : pendingSubscriptions) {
            if (pendingSubscription.getId() != null) {
                positionsById.put(pendingSubscription.getId(), index);
            }
            index++;
        }
        return positionsById;
    }

    private boolean useEventCollectionPendingQueue(Event event, Map<Integer, Boolean> collectionQueueEnabledById) {
        if (event == null || event.getEventCollection() == null || event.getEventCollection().getId() == null) {
            return false;
        }
        if (RequestContext.require().eventCollectionOptionDao() == null) {
            return false;
        }

        Integer collectionId = event.getEventCollection().getId();
        return collectionQueueEnabledById.computeIfAbsent(collectionId, id -> {
            Integer maxCollectionSubscriptions =
                    RequestContext.require().eventCollectionOptionDao().findIntOptionValue(id, EventCollectionOptionType.MAX_SUBSCRIPTIONS);
            return maxCollectionSubscriptions != null && maxCollectionSubscriptions > 0;
        });
    }

    @GET
    @LoggedOnly
    @Path("my/{playersubscriptionId}/eventcollection/{eventCollectionId}")
    public MustacheHtml selectEventCollectionVariant(
            @PathParam("playersubscriptionId") Integer playersubscriptionId,
            @PathParam("eventCollectionId") Integer eventCollectionId) {

        PlayerSubscription sub = RequestContext.require().playerSubscriptionDao().find(playersubscriptionId);
        if (sub == null) {
            throw new WebApplicationException("PlayerSubscription not found", Response.Status.NOT_FOUND);
        }

        if (!sub.getEvent().getEventCollection().getId().equals(eventCollectionId)) {
            throw new WebApplicationException("EventCollection does not match", Response.Status.BAD_REQUEST);
        }

        // Get player for rating validation
        IPlayer player = RequestContext.require().find().player(sub.getNrFfe(), null);
        if (player == null) {
            throw new WebApplicationException("Player not found", Response.Status.NOT_FOUND);
        }

        Integer currentEventId = sub.getEvent().getId();

        // Build map of selectable events and ineligibility reasons.
        Map<String, Boolean> canSelectEvent = new HashMap<>();
        Map<String, String> ineligibleReason = new HashMap<>();
        for (Event event : sub.getEvent().getEventCollection().getEvents()) {
            boolean canSelect = true;
            String reasonKey = null;

            // Get MIN_ELO and MAX_ELO options
            List<EventOption> options = RequestContext.require().eventOptionDao().findByEventId(event.getId());
            Integer minElo = null;
            Integer maxElo = null;
            Integer maxSubscriptions = null;

            for (EventOption option : options) {
                try {
                    if (option.getOptionType() == EventOptionType.MIN_ELO) {
                        minElo = Integer.parseInt(option.getValue());
                    } else if (option.getOptionType() == EventOptionType.MAX_ELO) {
                        maxElo = Integer.parseInt(option.getValue());
                    } else if (option.getOptionType() == EventOptionType.MAX_SUBSCRIPTIONS) {
                        maxSubscriptions = Integer.parseInt(option.getValue());
                    }
                } catch (NumberFormatException e) {
                    logger.debug("Invalid ELO value for event {}: {}", event.getId(), option.getValue());
                }
            }

            // Get player rating for this event type
            String ratingStr = "0";
            if (event.getEventType() == com.github.gcolin.event.EventType.RAPID) {
                ratingStr = player.getRapidRating() != null ? player.getRapidRating() : "0";
            } else if (event.getEventType() == com.github.gcolin.event.EventType.BLITZ) {
                ratingStr = player.getBlitzRating() != null ? player.getBlitzRating() : "0";
            } else {
                ratingStr = player.getRating() != null ? player.getRating() : "0";
            }
            // Extract numeric part only (handles ratings like "1550F" or "1550N")
            String numericRating = ratingStr.replaceAll("[^0-9]", "");
            Integer playerRating = 0;
            try {
                playerRating = Integer.parseInt(numericRating.isEmpty() ? "0" : numericRating);
            } catch (NumberFormatException e) {
                logger.debug("Invalid player rating for {}: {}", sub.getNrFfe(), ratingStr);
            }

            // Check if rating is within range
            if (minElo != null && playerRating < minElo) {
                canSelect = false;
                reasonKey = "eloTooLow";
            }
            if (maxElo != null && playerRating > maxElo) {
                canSelect = false;
                reasonKey = "eloTooHigh";
            }

            // Check if event has remaining subscriptions
            if (maxSubscriptions != null && maxSubscriptions > 0) {
                int currentCount = event.getSubscriptions() != null
                        ? event.getSubscriptions().size()
                        : 0;
                if (currentCount >= maxSubscriptions) {
                    canSelect = false;
                    reasonKey = "eventFull";
                }
            }

            // Keep current selection enabled even if it no longer matches constraints.
            if (event.getId().equals(currentEventId)) {
                canSelect = true;
                reasonKey = null;
            }

            canSelectEvent.put(String.valueOf(event.getId()), canSelect);
            if (!canSelect && reasonKey != null) {
                ineligibleReason.put(String.valueOf(event.getId()), reasonKey);
            }
        }

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("playerSubscription", sub);
        model.put("eventCollection", sub.getEvent().getEventCollection());
        model.put("events", sub.getEvent().getEventCollection().getEvents());
        model.put("currentEventId", currentEventId);
        model.put("canSelectEvent", canSelectEvent);
        model.put("ineligibleReason", ineligibleReason);

        return new MustacheHtml(model, "event/selectEventCollectionVariant.mustache");
    }

    @POST
    @LoggedOnly
    @Transactional
    @Path("my/{playersubscriptionId}/eventcollection/{eventCollectionId}")
    public Response updateEventCollectionVariant(
            @PathParam("playersubscriptionId") Integer playersubscriptionId,
            @PathParam("eventCollectionId") Integer eventCollectionId,
            @FormParam("eventId") Integer eventId) {
        if (eventId == null) {
            throw new WebApplicationException("eventId is required", Response.Status.BAD_REQUEST);
        }

        PlayerSubscription sub = RequestContext.require().playerSubscriptionDao().find(playersubscriptionId);
        if (sub == null) {
            throw new WebApplicationException("PlayerSubscription not found", Response.Status.NOT_FOUND);
        }

        if (!sub.getEvent().getEventCollection().getId().equals(eventCollectionId)) {
            throw new WebApplicationException("EventCollection does not match", Response.Status.BAD_REQUEST);
        }

        Event newEvent = RequestContext.require().eventDao().find(eventId);
        if (newEvent == null || !newEvent.getEventCollection().getId().equals(eventCollectionId)) {
            throw new WebApplicationException("Event not found or not in EventCollection", Response.Status.BAD_REQUEST);
        }

        // Update the subscription to point to the new event
        sub.setEvent(newEvent);
        RequestContext.require().playerSubscriptionDao().merge(sub);

        // Invalidate AppContext.get().caches()
        AppContext.get().caches().getEvent().invalidateAll();
        AppContext.get().caches().getAllEvents().invalidateAll();

        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                .path("event")
                .path("my")
                .queryParam("success", "changed")
                .build();
        return Response.seeOther(uri).build();
    }

    @GET
    @Path("{id:\\d+}")
    public MustacheHtml event(@PathParam("id") Integer id, @QueryParam("success") String success) throws IOException {

        EventCache cached = AppContext.get().caches().getEvent().getIfPresent(id.toString());

        if (cached == null) {
            cached = RequestContext.require().eventDao().buildCache(id);
            AppContext.get().caches().getEvent().put(id.toString(), cached);
        }

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("page", "event");
        model.put("event", cached.event);
        model.put("players", cached.players);
        model.put("missingPlayers", cached.missingPlayerCodes);
        model.put("eventInfo", cached.eventInfo);
        model.put("success", success);
        if (cached.event != null && cached.event.getName() != null && !cached.event.getName().isBlank()) {
            model.put("title", cached.event.getName());
        }

        return new MustacheHtml(model, "event/event.mustache");
    }

    @GET
    @Path("new")
    @RequireRole(RoleCode.ARBITRE)
    public MustacheHtml newE(@PathParam("id") Integer id) {
        return edit(null, null);
    }

    @GET
    @Path("{group}")
    public MustacheHtml events(
            @QueryParam("status") @DefaultValue("ACTIVE") String statusString,
            @PathParam("group") String group,
            @QueryParam("seasonId") Integer seasonId)
            throws IOException {
        String cacheKey = statusString + group + RequestContext.require().clubSeasonFilter().effectiveSeasonId(seasonId);
        EventsCache eventsCache = AppContext.get().caches().getAllEvents().getIfPresent(cacheKey);

        if (eventsCache == null) {
            eventsCache = new EventsCache();
            if (group != null) {
                eventsCache.group = RequestContext.require().eventGroupDao().findByShortname(group);
            }
            SeasonScope scope = RequestContext.require().clubSeasonFilter().resolve(seasonId);
            List<Event> events = group == null
                    ? RequestContext.require().eventDao().findByStatus(EventStatus.valueOf(statusString), scope)
                    : RequestContext.require().eventDao().findByStatus(EventStatus.valueOf(statusString), eventsCache.group, scope);
            
            events.forEach(RequestContext.require().eventDao()::fillSubscriptionLimits);
            RequestContext.require().eventDao().fillNbSubscriptions(events);
            RequestContext.require().eventDao().detachAll(events);

            eventsCache.events = events;
            logger.info(events.toString());
            if (eventsCache.group != null) {
                RequestContext.require().eventGroupDao().detach(eventsCache.group);
            }

            AppContext.get().caches().getAllEvents().put(cacheKey, eventsCache);
        }

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("page", "events");
        model.put("titleKey", "events.title");
        model.put("status", statusString);
        model.put("events", eventsCache.events);
        model.put("eventGroup", eventsCache.group);
        model.put("eventgroups", RequestContext.require().eventGroupFilter().getAll(group));
        RequestContext.require().clubSeasonFilter().addToModel(model, seasonId);
        if (eventsCache.group != null) {
            model.put("filterUrl", "/event/" + eventsCache.group.getShortname());
        } else {
            model.put("filterUrl", "/event");
        }
        return new MustacheHtml(model, "event/events.mustache");
    }

    @GET
    @Path("{id:\\d+}/edit")
    @RequireRole(RoleCode.ARBITRE)
    public MustacheHtml edit(@PathParam("id") Integer id, @QueryParam("success") String success) {
        Map<String, Object> model = new HashMap<String, Object>();
        boolean canEdit = RequestContext.require().loggedUser().hasRole(RoleCode.ARBITRE);
        model.put("readOnly", !canEdit);
        if (id != null && id != 0) {
            Event event = RequestContext.require().eventDao().find(id);
            Map<String, String> eventOptions = loadEventOptions(id);
            if (!canEdit) {
                eventOptions.remove(EventOptionType.FFE_PASSWORD.name());
            }
            model.put("eventOptions", eventOptions);
            model.put("event", event);
        } else {
            model.put("event", new Event());
        }
        model.put("eventGroups", RequestContext.require().eventGroupDao().all());
        model.put("eventCollections", RequestContext.require().eventCollectionDao().allOrdered());
        model.put("success", success);
        return new MustacheHtml(model, "event/eventEdit.mustache");
    }

    @POST
    @Path("{id:\\d+}/description/preview")
    @RequireRole(RoleCode.ARBITRE)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response previewDescription(
            @PathParam("id") Integer eventId, @FormParam("description") String description) {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(description == null ? "" : description);
        HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
        return Response.ok(renderer.render(document), MediaType.TEXT_HTML).build();
    }

    @POST
    @RequireRole(RoleCode.ARBITRE)
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA})
    public Response postEventEdit(
            @FormParam("descriptionOnly") @DefaultValue("false") boolean descriptionOnly,
            @FormParam("optionsOnly") @DefaultValue("false") boolean optionsOnly,
            @FormParam("eventId") Integer eventId,
            @FormParam("description") String description,
            @FormParam("ffeId") String ffeId,
            @FormParam("minElo") String minElo,
            @FormParam("maxElo") String maxElo,
            @FormParam("ffePassword") String ffePassword,
            @FormParam("name") String name,
            @FormParam("startDate") String startDate,
            @FormParam("endDate") String endDate,
            @FormParam("status") String status,
            @FormParam("eventType") String eventType,
            @FormParam("price") String price,
            @FormParam("youngprice") String youngprice,
            @FormParam("eventgroup") String eventgroup,
            @FormParam("eventcollection") String eventCollection,
            @FormParam("rondes") String rondes,
            @FormParam("cadence") String cadence,
            @FormParam("pairing") String pairing,
            @FormParam("maxSubscriptions") String maxSubscriptions,
            @FormParam("pointage") String pointage) {
        if (descriptionOnly) {
            if (eventId != null) {
                RequestContext.require().eventDao().updateDescription(eventId, description);
            }
        } else if (optionsOnly) {
            if (eventId != null) {
                RequestContext.require().eventOptionDao().setOption(eventId, EventOptionType.FFE_ID, ffeId != null ? ffeId : "");
                RequestContext.require().eventOptionDao().setOption(eventId, EventOptionType.MIN_ELO, minElo != null ? minElo : "");
                RequestContext.require().eventOptionDao().setOption(eventId, EventOptionType.MAX_ELO, maxElo != null ? maxElo : "");
                RequestContext.require().eventOptionDao().setOption(
                        eventId, EventOptionType.FFE_PASSWORD, ffePassword != null ? ffePassword : "");
                RequestContext.require().eventOptionDao().setOption(
                        eventId, EventOptionType.POINTAGE, "1".equals(pointage) ? "1" : "0");
            }
        } else {
            eventId = RequestContext.require().eventDao().saveEvent(
                    eventId,
                    name,
                    startDate,
                    endDate,
                    status,
                    eventType,
                    price,
                    youngprice,
                    eventgroup,
                    eventCollection,
                    rondes,
                    cadence,
                    pairing,
                    maxSubscriptions);
        }
        AppContext.get().caches().getEvent().invalidateAll();
        AppContext.get().caches().getAllEvents().invalidateAll();
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder()
                .path("event")
                .path(eventId.toString())
                .path("edit")
                .queryParam("success", "save")
                .build();
        return Response.seeOther(uri).build();
    }

    private Map<String, String> loadEventOptions(int eventId) {
        Map<String, String> options = new HashMap<>();
        for (EventOption option : RequestContext.require().eventOptionDao().findByEventId(eventId)) {
            options.put(option.getOptionType().name(), option.getValue());
        }
        return options;
    }

    @GET
    @Path("{id:\\d+}/pointe")
    @LoggedOnly
    public Response pointe(
            @PathParam("id") Integer eventId, @QueryParam("subId") Integer subId) {
        if (subId == null) {
            throw new WebApplicationException("subId is required", Response.Status.BAD_REQUEST);
        }
        PlayerSubscription sub = RequestContext.require().playerSubscriptionDao().findWithEvent(subId);
        if (sub == null || sub.getEvent() == null || !eventId.equals(sub.getEvent().getId())) {
            throw new WebApplicationException("PlayerSubscription not found", Response.Status.NOT_FOUND);
        }
        if (!RequestContext.require().loggedUser().getEmail().equals(sub.getCreationUser())) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }
        if (sub.getStatus() != PlayerSubscriptionStatus.PAID) {
            throw new WebApplicationException("subscription not paid", Response.Status.BAD_REQUEST);
        }
        RequestContext.require().eventDao().fillSubscriptionLimits(sub.getEvent());
        if (!sub.getEvent().isPointageEnabled()) {
            throw new WebApplicationException("pointage not enabled", Response.Status.BAD_REQUEST);
        }
        if (sub.getAttendanceAt() == null) {
            // Dao @Transactional commits before desk snapshot so a fresh EM sees attendance.
            sub.setAttendanceAt(LocalDateTime.now());
            RequestContext.require().playerSubscriptionDao().merge(sub);
            AppContext.get().caches().getEvent().invalidateAll();
            AppContext.get().eventDeskHub().publishSnapshot(eventId, AppContext.get().eventDeskService().snapshot(eventId), null);
        }
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path("my").build();
        return Response.seeOther(uri).build();
    }

    @GET
    @Path("{id:\\d+}/unregister")
    @LoggedOnly
    public Response unregister(@PathParam("id") Integer eventId, @QueryParam("nrffe") String nrffe)
            throws IOException {
        if (nrffe == null || nrffe.isBlank()) {
            throw new WebApplicationException("nrffe is required", Response.Status.BAD_REQUEST);
        }

        Event event = RequestContext.require().eventDao().find(eventId);
        if (event == null) {
            throw new WebApplicationException("event not found", Response.Status.NOT_FOUND);
        }

        PlayerSubscription sub = RequestContext.require().playerSubscriptionDao().findByEventAndNrffe(event, nrffe);
        if (sub == null) {
            throw new WebApplicationException(nrffe + " is not present in event " + eventId, Response.Status.NOT_FOUND);
        }
        if (!RequestContext.require().loggedUser().getEmail().equals(sub.getCreationUser())) {
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        EventCache cached = AppContext.get().caches().getEvent().getIfPresent(eventId.toString());

        if (cached == null) {
            cached = RequestContext.require().eventDao().buildCache(eventId);
            AppContext.get().caches().getEvent().put(eventId.toString(), cached);
        }

        if (!RequestContext.require().registerService().unregisterPlayerToEvent(eventId, nrffe)) {
            throw new WebApplicationException(nrffe + " is not present in event " + eventId, Response.Status.NOT_FOUND);
        }
        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path("my").build();
        return Response.seeOther(uri).build();
    }

    @GET
    @Path("{id:\\d+}/unregister-pending")
    @LoggedOnly
    public Response unregisterPending(
            @PathParam("id") Integer eventId,
            @QueryParam("nrffe") String nrffe) {
        if (nrffe == null || nrffe.isBlank()) {
            throw new WebApplicationException("nrffe is required", Response.Status.BAD_REQUEST);
        }

        Event event = RequestContext.require().eventDao().find(eventId);
        if (event == null) {
            throw new WebApplicationException("event not found", Response.Status.NOT_FOUND);
        }

        var pending = RequestContext.require().playerPendingSubscriptionDao()
                .findByEventAndNrffeAndCreationUser(event, nrffe, RequestContext.require().loggedUser().getEmail());

        if (pending == null) {
            throw new WebApplicationException("pending subscription not found", Response.Status.NOT_FOUND);
        }

        RequestContext.require().playerPendingSubscriptionDao().remove(pending);
        AppContext.get().caches().getEvent().invalidateAll();
        AppContext.get().caches().getDebtCache().invalidateAll();

        URI uri = RequestContext.require().uriInfo().getBaseUriBuilder().path("event").path("my").build();
        return Response.seeOther(uri).build();
    }
}
