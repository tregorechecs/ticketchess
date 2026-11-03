package com.github.gcolin.event;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.platform.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds Mustache view models for the admin events tree. */
public final class EventAdminMustacheEnricher {

    private EventAdminMustacheEnricher() {}

    public static void enrich(
            Map<String, Object> model, List<EventAdminGroupNode> groups, Messages messages, LoggedUser user) {
        boolean showCreateEvent = user != null && user.hasRole("EVENT_ADMIN");
        boolean showAdminStatisticsLink = user != null && user.hasRole("ADMIN");
        boolean showCollectionMailLink = user != null && user.canManageEvents();
        model.put("showCreateEvent", showCreateEvent);
        model.put("showAdminStatisticsLink", showAdminStatisticsLink);
        model.put("showCollectionMailLink", showCollectionMailLink);

        List<Map<String, Object>> treeGroups = new ArrayList<>();
        if (groups != null) {
            for (EventAdminGroupNode groupNode : groups) {
                treeGroups.add(toGroupRow(groupNode, messages, showAdminStatisticsLink, showCollectionMailLink));
            }
        }
        model.put("treeGroups", treeGroups);
        model.put("treeGroupsEmpty", treeGroups.isEmpty());
        model.put("treeFilterScript", !treeGroups.isEmpty());
    }

    private static Map<String, Object> toGroupRow(
            EventAdminGroupNode groupNode,
            Messages messages,
            boolean showAdminStatisticsLink,
            boolean showCollectionMailLink) {
        Map<String, Object> row = new HashMap<>();
        row.put("ungrouped", groupNode.isUngrouped());
        row.put("showAdminStatisticsLink", showAdminStatisticsLink);
        row.put("showCollectionMailLink", showCollectionMailLink);
        if (groupNode.isUngrouped()) {
            row.put("searchText", messages.get("adminEvents.ungrouped"));
        } else {
            EventGroup group = groupNode.getGroup();
            row.put("group", group);
            row.put(
                    "searchText",
                    (group.getName() != null ? group.getName() : "")
                            + " "
                            + (group.getShortname() != null ? group.getShortname() : ""));
        }

        List<Map<String, Object>> collections = new ArrayList<>();
        for (EventAdminCollectionNode collectionNode : groupNode.getCollections()) {
            collections.add(toCollectionRow(collectionNode, groupNode, messages, showCollectionMailLink));
        }
        row.put("collections", collections);

        List<Map<String, Object>> events = new ArrayList<>();
        for (Event event : groupNode.getEvents()) {
            events.add(toEventRow(event, groupNode, messages, null));
        }
        row.put("events", events);
        row.put("hasChildren", !collections.isEmpty() || !events.isEmpty());
        return row;
    }

    private static Map<String, Object> toCollectionRow(
            EventAdminCollectionNode collectionNode,
            EventAdminGroupNode groupNode,
            Messages messages,
            boolean showCollectionMailLink) {
        Map<String, Object> row = new HashMap<>();
        row.put("collection", collectionNode.getCollection());
        row.put("showCollectionMailLink", showCollectionMailLink);
        List<Map<String, Object>> events = new ArrayList<>();
        for (Event event : collectionNode.getEvents()) {
            events.add(toEventRow(event, groupNode, messages, collectionNode.getCollection()));
        }
        row.put("events", events);
        return row;
    }

    private static Map<String, Object> toEventRow(
            Event event, EventAdminGroupNode groupNode, Messages messages, EventCollection collection) {
        Map<String, Object> row = new HashMap<>();
        row.put("event", event);
        row.put(
                "startDateFormatted",
                event.getStartDate() != null ? messages.formatDate(event.getStartDate()) : "");
        row.put("hasStartDate", event.getStartDate() != null);
        String groupLabel;
        if (groupNode.isUngrouped()) {
            groupLabel = messages.get("adminEvents.ungrouped");
        } else {
            EventGroup group = groupNode.getGroup();
            groupLabel =
                    (group.getName() != null ? group.getName() : "")
                            + " "
                            + (group.getShortname() != null ? group.getShortname() : "");
        }
        String collectionName = collection != null && collection.getName() != null ? collection.getName() : "";
        row.put(
                "searchText",
                (event.getName() != null ? event.getName() : "")
                        + " "
                        + row.get("startDateFormatted")
                        + " "
                        + groupLabel
                        + " "
                        + collectionName
                        + " "
                        + event.getId());
        return row;
    }
}
