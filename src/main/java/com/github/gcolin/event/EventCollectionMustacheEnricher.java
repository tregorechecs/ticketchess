package com.github.gcolin.event;

import com.github.gcolin.platform.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventCollectionMustacheEnricher {

    private EventCollectionMustacheEnricher() {}

    public static void enrichListPage(Map<String, Object> model, Messages messages) {
        if (model.get("error") instanceof String error && !error.isBlank()) {
            model.put("errorMessageKey", "eventcollection.error." + error);
        }
        @SuppressWarnings("unchecked")
        List<EventCollection> collections = (List<EventCollection>) model.get("eventCollections");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (collections != null) {
            for (EventCollection ec : collections) {
                rows.add(collectionListRow(ec));
            }
        }
        model.put("eventCollectionRows", rows);
        model.put("eventCollectionsEmpty", rows.isEmpty());
    }

    public static void enrichEditPage(Map<String, Object> model, Messages messages) {
        if (model.get("error") instanceof String error && !error.isBlank()) {
            model.put("errorMessageKey", "eventcollection.error." + error);
        }
        model.put("showSaveSuccess", "save".equals(model.get("success")));
        if (!(model.get("eventCollection") instanceof EventCollection eventCollection)) {
            return;
        }
        model.put(
                "collectionNameValue",
                eventCollection.getName() != null ? eventCollection.getName() : "");
        if (Boolean.TRUE.equals(model.get("createMode"))) {
            return;
        }
        int nb = eventCollection.getNbSubscriptions() != null ? eventCollection.getNbSubscriptions() : 0;
        if (eventCollection.getMaxSubscribe() != null) {
            model.put(
                    "subscriptionsSummaryText",
                    messages.get("eventcollection.currentSubscriptionsWithMax", nb, eventCollection.getMaxSubscribe()));
        } else {
            model.put("subscriptionsSummaryText", messages.get("eventcollection.currentSubscriptions", nb));
        }
        model.put("hasMaxSubscribe", eventCollection.getMaxSubscribe() != null);
        model.put(
                "maxSubscribeInputValue",
                eventCollection.getMaxSubscribe() != null ? String.valueOf(eventCollection.getMaxSubscribe()) : "");
        model.put("removeDisabled", eventCollection.getEventCount() > 0);
        List<Map<String, Object>> linkedEvents = new ArrayList<>();
        if (eventCollection.getEvents() != null) {
            for (Event event : eventCollection.getEvents()) {
                linkedEvents.add(StatisticsMustacheEnricher.collectionEventRow(event));
            }
        }
        model.put("linkedEvents", linkedEvents);
        model.put("hasLinkedEvents", !linkedEvents.isEmpty());
    }

    private static Map<String, Object> collectionListRow(EventCollection ec) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", ec.getId());
        row.put("name", ec.getName());
        row.put("eventCount", ec.getEventCount());
        if (ec.getMaxSubscribe() != null) {
            row.put("hasMaxSubscribe", true);
            row.put("nbSubscriptions", ec.getNbSubscriptions() != null ? ec.getNbSubscriptions() : 0);
            row.put("maxSubscribe", ec.getMaxSubscribe());
        } else {
            row.put("hasMaxSubscribe", false);
        }
        return row;
    }
}
