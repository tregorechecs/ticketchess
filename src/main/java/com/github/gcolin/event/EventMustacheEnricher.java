package com.github.gcolin.event;

import com.github.gcolin.platform.Messages;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EventMustacheEnricher {

    private EventMustacheEnricher() {}

    public static void enrichPublicEventPage(Map<String, Object> model, Messages messages) {
        if (!(model.get("event") instanceof Event event)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<DisplayPlayer> sourcePlayers =
                model.get("players") instanceof List<?> list ? (List<DisplayPlayer>) list : List.of();
        int playerCount = sourcePlayers.size();

        model.put("eventIsActive", event.getStatus() == EventStatus.ACTIVE);
        model.put("eventNotFree", !event.isFree());

        if (event.getFfeId() != null && !event.getFfeId().isBlank()) {
            model.put("eventHasFfeId", true);
            model.put(
                    "ffeUrl",
                    "https://www.echecs.asso.fr/FicheTournoi.aspx?Ref="
                            + URLEncoder.encode(event.getFfeId(), StandardCharsets.UTF_8));
        }

        Object eventInfo = model.get("eventInfo");
        if (eventInfo instanceof String html && !html.isBlank()) {
            model.put("hasEventInfoHtml", true);
            model.put("eventInfoHtml", html);
        }

        Integer maxSubscriptions = event.getMaxSubscriptions();
        int eventRemaining =
                maxSubscriptions != null ? Math.max(0, maxSubscriptions - playerCount) : 0;
        if (maxSubscriptions != null && eventRemaining > 0) {
            model.put("hasRemainingSubscriptions", true);
            model.put("remainingSubscriptions", eventRemaining);
        }

        EventCollection collection = event.getEventCollection();
        boolean eventFull = maxSubscriptions != null && eventRemaining == 0;
        int collectionRemaining = 0;
        boolean collectionFull = false;
        if (collection != null
                && collection.getMaxSubscribe() != null
                && collection.getNbSubscriptions() != null) {
            collectionRemaining =
                    Math.max(0, collection.getMaxSubscribe() - collection.getNbSubscriptions());
            collectionFull = collectionRemaining == 0;
            if (!eventFull && collectionRemaining > 0) {
                model.put("hasCollectionRemaining", true);
                model.put("collectionRemainingSubscriptions", collectionRemaining);
            }
        }

        boolean showPendingQueue = eventFull || collectionFull;
        model.put("showPendingQueue", showPendingQueue);
        if (showPendingQueue) {
            model.put(
                    "fullMessageKey",
                    collectionFull && collection != null && collection.getMaxSubscribe() != null
                            ? "event.collectionFull"
                            : "event.full");
        }
        model.put(
                "registerActionKey",
                showPendingQueue ? "action.joinPendingQueue" : "action.register");

        List<Map<String, Object>> playerRows = new ArrayList<>();
        for (DisplayPlayer pl : sourcePlayers) {
            Map<String, Object> row = toEventPlayerRow(pl);
            if (pl.getStatus() != null) {
                row.put("statusMessageKey", "playerStatus." + pl.getStatus().name());
            }
            playerRows.add(row);
        }
        model.put("players", playerRows);
    }

    public static void enrichEditPage(Map<String, Object> model, Messages messages) {
        if (!(model.get("event") instanceof Event event)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> eventOptions = model.get("eventOptions") instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : new HashMap<>();
        model.put("eventOptions", eventOptions);

        if (!model.containsKey("eventInfo")) {
            Map<String, Object> emptyInfo = new HashMap<>();
            emptyInfo.put("description", "");
            model.put("eventInfo", emptyInfo);
        }

        model.put("eventHasId", event.getId() != null);
        model.put(
                "eventDisplayName",
                event.getName() != null ? event.getName() : String.valueOf(event.getId()));
        model.put("startDateIso", formatDateIso(event.getStartDateAsDate()));
        model.put("endDateIso", formatDateIso(event.getEndDateAsDate()));
        model.put("priceValue", event.getPrice() != null ? event.getPrice() : 0);
        model.put("youngPriceValue", event.getYoungPrice() != null ? event.getYoungPrice() : 0);
        model.put(
                "roundsValue",
                eventOptions.getOrDefault(EventOptionType.ROUNDS.name(), ""));

        EventType eventType = event.getEventType();
        model.put("eventTypeIsSTANDARD", eventType == EventType.STANDARD);
        model.put("eventTypeIsRAPID", eventType == EventType.RAPID);
        model.put("eventTypeIsBLITZ", eventType == EventType.BLITZ);

        EventStatus status = event.getStatus();
        model.put("eventStatusIsDRAFT", status == EventStatus.DRAFT);
        model.put("eventStatusIsACTIVE", status == EventStatus.ACTIVE);
        model.put("eventStatusIsONGOING", status == EventStatus.ONGOING);
        model.put("eventStatusIsCOMPLETED", status == EventStatus.COMPLETED);

        String pairing = eventOptions.get(EventOptionType.PAIRING.name());
        if (pairing == null || pairing.isBlank()) {
            pairing = "Standard";
        }
        model.put("pairingIsStandard", "Standard".equals(pairing));
        model.put("pairingIsHaley", "Haley".equals(pairing));
        model.put("pairingIsHaleySoft", "HaleySoft".equals(pairing));
        model.put("pairingIsSAD", "SAD".equals(pairing));
        model.put("pairingIsNicois", "Nicois".equals(pairing));
        model.put("pairingIsBerger", "Berger".equals(pairing));

        model.put("pointageChecked", "1".equals(eventOptions.get(EventOptionType.POINTAGE.name())));

        String ffeId = eventOptions.get(EventOptionType.FFE_ID.name());
        if (ffeId != null && !ffeId.isBlank()) {
            model.put("hasFfeId", true);
            model.put(
                    "ffeEditUrl",
                    "https://www.echecs.asso.fr/FicheTournoi.aspx?Ref="
                            + URLEncoder.encode(ffeId, StandardCharsets.UTF_8));
        }

        Integer selectedGroupId =
                event.getEventGroup() != null ? event.getEventGroup().getId() : null;
        Integer selectedCollectionId =
                event.getEventCollection() != null ? event.getEventCollection().getId() : null;

        @SuppressWarnings("unchecked")
        List<EventGroup> eventGroups =
                model.get("eventGroups") instanceof List<?> list ? (List<EventGroup>) list : List.of();
        model.put("eventGroupOptions", selectOptions(eventGroups, selectedGroupId, EventGroup::getId, EventGroup::getName));

        @SuppressWarnings("unchecked")
        List<EventCollection> eventCollections = model.get("eventCollections") instanceof List<?> list
                ? (List<EventCollection>) list
                : List.of();
        model.put(
                "eventCollectionOptions",
                selectOptions(
                        eventCollections,
                        selectedCollectionId,
                        EventCollection::getId,
                        EventCollection::getName));
    }

    public static void enrichRegisterPage(Map<String, Object> model, Messages messages) {
        if (!(model.get("event") instanceof Event event)) {
            return;
        }
        model.put("eventNotFree", !event.isFree());

        boolean registrationClosed = Boolean.TRUE.equals(model.get("registrationClosed"));
        model.put("selectActionKey", registrationClosed ? "action.joinPendingQueue" : "action.select");

        @SuppressWarnings("unchecked")
        List<DisplayPlayer> players =
                model.get("players") instanceof List<?> list ? (List<DisplayPlayer>) list : null;
        if (players != null) {
            model.put("hasPlayers", !players.isEmpty());
            model.put("showNoPlayer", players.isEmpty());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (DisplayPlayer pl : players) {
                rows.add(toRegisterPlayerRow(pl));
            }
            model.put("players", rows);
        }

        @SuppressWarnings("unchecked")
        List<DisplayPlayer> favorites =
                model.get("favoritePlayers") instanceof List<?> list ? (List<DisplayPlayer>) list : List.of();
        model.put("hasFavoritePlayers", !favorites.isEmpty());
        List<Map<String, Object>> favoriteRows = new ArrayList<>();
        for (DisplayPlayer pl : favorites) {
            Map<String, Object> row = toRegisterPlayerRow(pl);
            row.put("nrffeId", pl.getNrffeId());
            favoriteRows.add(row);
        }
        model.put("favoritePlayers", favoriteRows);
    }

    public static void enrichMyEventsPage(Map<String, Object> model, Messages messages) {
        @SuppressWarnings("unchecked")
        List<Event> events = model.get("events") instanceof List<?> list ? (List<Event>) list : List.of();
        @SuppressWarnings("unchecked")
        List<Event> pendingEvents =
                model.get("pendingEvents") instanceof List<?> list ? (List<Event>) list : List.of();

        model.put("noMyEvents", events.isEmpty() && pendingEvents.isEmpty());
        model.put("hasPendingEvents", !pendingEvents.isEmpty());

        boolean stripePublic = model.get("stripePublic") != null;
        boolean stripeSimulated = Boolean.TRUE.equals(model.get("stripeSimulated"));
        boolean stripeCardEnabled = !Boolean.FALSE.equals(model.get("stripeCardEventsEnabled"));
        boolean bankTransferEnabled = !Boolean.FALSE.equals(model.get("bankTransferEventsEnabled"));
        boolean ribAvailable = Boolean.TRUE.equals(model.get("ribAvailable"));
        boolean hasEventDebt = Boolean.TRUE.equals(model.get("hasEventDebt"));

        model.put("showCardPay", hasEventDebt && (stripePublic || stripeSimulated) && stripeCardEnabled);
        model.put("showBankTransfer", hasEventDebt && ribAvailable && bankTransferEnabled);
        model.put("showStripeJs", stripePublic);

        @SuppressWarnings("unchecked")
        List<?> paidPayments = model.get("paidPayments") instanceof List<?> list ? list : List.of();
        model.put("hasPaidPayments", !paidPayments.isEmpty());

        List<Map<String, Object>> eventRows = new ArrayList<>();
        for (Event event : events) {
            eventRows.add(toMyEventsEventRow(event, messages, false));
        }
        model.put("events", eventRows);

        List<Map<String, Object>> pendingRows = new ArrayList<>();
        for (Event event : pendingEvents) {
            pendingRows.add(toMyEventsEventRow(event, messages, true));
        }
        model.put("pendingEvents", pendingRows);
    }

    public static void enrichEventsListPage(Map<String, Object> model, Messages messages) {
        @SuppressWarnings("unchecked")
        List<Event> events = model.get("events") instanceof List<?> list ? (List<Event>) list : List.of();
        model.put("hasEvents", !events.isEmpty());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Event event : events) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", event.getId());
            row.put("name", event.getName());
            row.put(
                    "startDateDisplay",
                    event.getStartDateAsDate() != null ? event.getStartDateAsDate().toString() : "");
            if (event.getEventType() != null) {
                row.put("eventTypeMessageKey", "type." + event.getEventType().name());
            }
            boolean eventFull = event.getMaxSubscriptions() != null
                    && event.getNbSubscriptions() != null
                    && event.getMaxSubscriptions().equals(event.getNbSubscriptions());
            EventCollection collection = event.getEventCollection();
            boolean collectionFull = collection != null
                    && collection.getMaxSubscribe() != null
                    && collection.getNbSubscriptions() != null
                    && collection.getMaxSubscribe().equals(collection.getNbSubscriptions());
            row.put("full", eventFull || collectionFull);
            rows.add(row);
        }
        model.put("events", rows);
    }

    private static Map<String, Object> toMyEventsEventRow(Event event, Messages messages, boolean pending) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", event.getId());
        row.put("name", event.getName());
        row.put("notFree", !event.isFree());
        boolean active = event.getStatus() == EventStatus.ACTIVE;
        boolean pointageEnabled = event.isPointageEnabled();
        Integer collectionId =
                event.getEventCollection() != null ? event.getEventCollection().getId() : null;

        List<Map<String, Object>> playerRows = new ArrayList<>();
        if (event.getPlayers() != null) {
            for (DisplayPlayer pl : event.getPlayers()) {
                Map<String, Object> playerRow = basePlayerRow(pl);
                playerRow.put("id", pl.getId());
                playerRow.put("editable", pl.isEditable());
                playerRow.put("eventId", event.getId());
                playerRow.put("subId", pl.getSubId());
                playerRow.put("nrffeId", pl.getNrffeId());
                if (pending) {
                    playerRow.put(
                            "pendingQueueAheadText",
                            pl.getPendingQueueAheadText() != null
                                    ? pl.getPendingQueueAheadText()
                                    : messages.get(
                                            "myevents.pendingQueue.ahead",
                                            pl.getPendingQueueAhead() != null ? pl.getPendingQueueAhead() : 0));
                } else {
                    Double price = pl.getPrice();
                    playerRow.put("priceDisplay", price != null ? String.valueOf(price) : "");
                    if (pl.getStatus() != null) {
                        playerRow.put("statusMessageKey", "playerStatus." + pl.getStatus().name());
                        playerRow.put("paid", pl.getStatus() == PlayerSubscriptionStatus.PAID);
                        playerRow.put("notCancelled", pl.getStatus() != PlayerSubscriptionStatus.CANCELLED);
                    }
                    playerRow.put("activeStatus", active);
                    playerRow.put("pointageEnabled", pointageEnabled);
                    playerRow.put("pointed", pl.getAttendanceAt() != null);
                    if (collectionId != null) {
                        playerRow.put("hasEventCollection", true);
                        playerRow.put("eventCollectionId", collectionId);
                    }
                }
                playerRows.add(playerRow);
            }
        }
        row.put("players", playerRows);
        return row;
    }

    private static Map<String, Object> toEventPlayerRow(DisplayPlayer pl) {
        Map<String, Object> row = basePlayerRow(pl);
        row.put("subId", pl.getSubId());
        if (pl.getFideTitre() != null) {
            row.put("fideTitre", pl.getFideTitre());
            row.put("fideTitreMessageKey", "fidetitre." + pl.getFideTitre());
        }
        return row;
    }

    private static Map<String, Object> toRegisterPlayerRow(DisplayPlayer pl) {
        Map<String, Object> row = basePlayerRow(pl);
        row.put("price", pl.getPrice());
        if (pl.getFideTitre() != null) {
            row.put("fideTitre", pl.getFideTitre());
            row.put("fideTitreMessageKey", "fidetitre." + pl.getFideTitre());
        }
        return row;
    }

    private static Map<String, Object> basePlayerRow(DisplayPlayer pl) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", pl.getName());
        row.put("firstname", pl.getFirstname());
        String licence = pl.getLicence();
        row.put("licence", licence);
        row.put("licenceOrEmpty", licence != null ? licence : "");
        row.put("fullName", ((pl.getName() != null ? pl.getName() : "") + " "
                        + (pl.getFirstname() != null ? pl.getFirstname() : ""))
                .trim());
        row.put("category", pl.getCategory());
        row.put("categoryOrEmpty", pl.getCategory() != null ? pl.getCategory() : "");
        row.put("ratingOrEmpty", pl.getRating() != null ? pl.getRating() : "");
        row.put("ratingSortValue", ratingSortValue(pl.getRating()));
        row.put("clubOrEmpty", pl.getClub() != null ? pl.getClub() : "");
        return row;
    }

    private static String ratingSortValue(String rating) {
        if (rating == null || rating.isBlank()) {
            return "0";
        }
        String normalized = rating.replace(",", ".").replaceAll("[^0-9.-]", "");
        try {
            return String.valueOf(Double.parseDouble(normalized));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private static String formatDateIso(java.util.Date date) {
        if (date == null) {
            return "";
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static <T> List<Map<String, Object>> selectOptions(
            List<T> items, Integer selectedId, java.util.function.Function<T, Integer> idFn, java.util.function.Function<T, String> labelFn) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (T item : items) {
            Map<String, Object> opt = new HashMap<>();
            Integer id = idFn.apply(item);
            opt.put("value", id);
            opt.put("label", labelFn.apply(item));
            opt.put("selected", Objects.equals(id, selectedId));
            options.add(opt);
        }
        return options;
    }
}
