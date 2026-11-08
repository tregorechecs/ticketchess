package com.github.gcolin.event;

import com.github.gcolin.platform.Messages;
import com.github.gcolin.platform.ServiceUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Precomputes Mustache-friendly view data for statistics templates. */
public final class StatisticsMustacheEnricher {

    private StatisticsMustacheEnricher() {}

    public static void enrichReport(Map<String, Object> model, Messages messages) {
        if (!model.containsKey("total") || !model.containsKey("byClub")) {
            return;
        }
        if (model.get("event") instanceof Event event && event.getName() != null) {
            model.put("title", event.getName() + " \u2014 " + messages.get("event.statistics"));
        }
        if (model.get("eventCollection") instanceof EventCollection collection && collection.getName() != null) {
            model.put("title", collection.getName() + " \u2014 " + messages.get("event.statistics"));
        }
        int total = ((Number) model.get("total")).intValue();
        double totalAmount = toDouble(model.get("totalAmount"));
        double totalUnpaid = toDouble(model.get("totalUnpaidAmount"));
        model.put("totalSummaryText", messages.get("statistics.event.total", total));
        model.put("totalAmountFormatted", formatEuro(totalAmount));
        model.put("totalUnpaidAmountFormatted", formatEuro(totalUnpaid));
        model.put("totalExpectedFormatted", formatEuro(totalAmount + totalUnpaid));

        @SuppressWarnings("unchecked")
        Map<String, Double> amountByPaymentType = (Map<String, Double>) model.get("amountByPaymentType");
        List<Map<String, Object>> paymentTypeRows = new ArrayList<>();
        if (amountByPaymentType != null && !amountByPaymentType.isEmpty()) {
            model.put("hasAmountByPaymentType", true);
            for (Map.Entry<String, Double> entry : amountByPaymentType.entrySet()) {
                Map<String, Object> row = new HashMap<>();
                row.put("labelKey", "payment.entry.key." + entry.getKey());
                double value = entry.getValue() != null ? entry.getValue() : 0.0;
                row.put("amountFormatted", formatEuro(value));
                row.put("percentFormatted", formatPercent(totalAmount > 0 ? value * 100.0 / totalAmount : 0.0));
                paymentTypeRows.add(row);
            }
        }
        model.put("paymentTypeRows", paymentTypeRows);

        enrichCountMap(model, "byClub", "byClubRows", total);
        enrichCountMap(model, "byCategory", "byCategoryRows", total);
        enrichCountMap(model, "byFederation", "byFederationRows", total);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agePyramid = (List<Map<String, Object>>) model.get("agePyramid");
        int agePyramidMax = model.get("agePyramidMax") instanceof Number n ? n.intValue() : 0;
        List<Map<String, Object>> agePyramidRows = new ArrayList<>();
        if (agePyramid != null && !agePyramid.isEmpty()) {
            model.put("hasAgePyramid", true);
            for (Map<String, Object> source : agePyramid) {
                agePyramidRows.add(agePyramidRow(source, agePyramidMax));
            }
        }
        model.put("agePyramidRows", agePyramidRows);

        enrichPlayerAmountList(model, "unknownPaymentPlayers", "unknownPaymentPlayerRows", false);
        enrichPlayerAmountList(model, "multiEventPlayers", "multiEventPlayerRows", true);
    }

    public static void enrichDashboard(Map<String, Object> model, Messages messages) {
        if (!model.containsKey("totalPayments") || !model.containsKey("closestEvents")) {
            return;
        }
        long totalPaymentsCents = toLong(model.get("totalPayments"));
        long unpaidCents = toLong(model.get("unpaidPlayerSubscriptions"));
        model.put("totalPaymentsFormatted", formatEuro(ServiceUtils.toEuros(totalPaymentsCents)));
        model.put(
                "totalWithUnpaidFormatted",
                formatEuro(ServiceUtils.toEuros(totalPaymentsCents + unpaidCents)));

        @SuppressWarnings("unchecked")
        List<?> closest = (List<?>) model.get("closestEvents");
        @SuppressWarnings("unchecked")
        List<?> top = (List<?>) model.get("topEvents");
        model.put("closestEventsCountText", messages.get("statistics.closestEventsCount", closest != null ? closest.size() : 0));
        model.put("topEventsCountText", messages.get("statistics.topEventsCount", top != null ? top.size() : 0));
        model.put("closestEventsEmpty", closest == null || closest.isEmpty());
        model.put("topEventsEmpty", top == null || top.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static void enrichCountMap(
            Map<String, Object> model, String mapKey, String rowsKey, int total) {
        Map<String, Integer> map = (Map<String, Integer>) model.get(mapKey);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (map != null) {
            model.put(mapKey + "Count", map.size());
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                Map<String, Object> row = new HashMap<>();
                row.put("label", entry.getKey());
                int count = entry.getValue() != null ? entry.getValue() : 0;
                row.put("count", count);
                row.put("percentFormatted", formatPercent(total > 0 ? count * 100.0 / total : 0.0));
                rows.add(row);
            }
        } else {
            model.put(mapKey + "Count", 0);
        }
        model.put(rowsKey, rows);
    }

    private static Map<String, Object> agePyramidRow(Map<String, Object> source, int max) {
        Map<String, Object> row = new HashMap<>();
        int male = intValue(source.get("male"));
        int female = intValue(source.get("female"));
        row.put("label", String.valueOf(source.get("label")));
        row.put("male", male);
        row.put("female", female);
        row.put("maleBarWidth", formatPercent(max > 0 ? male * 100.0 / max : 0.0));
        row.put("femaleBarWidth", formatPercent(max > 0 ? female * 100.0 / max : 0.0));
        row.put("maleBarMinWidth", male > 0 ? "4px" : "0");
        row.put("femaleBarMinWidth", female > 0 ? "4px" : "0");
        return row;
    }

    @SuppressWarnings("unchecked")
    private static void enrichPlayerAmountList(
            Map<String, Object> model, String listKey, String rowsKey, boolean includeEvents) {
        List<Map<String, Object>> source = (List<Map<String, Object>>) model.get(listKey);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (source != null && !source.isEmpty()) {
            model.put("has" + capitalize(listKey), true);
            model.put(listKey + "Count", source.size());
            for (Map<String, Object> item : source) {
                Map<String, Object> row = new HashMap<>();
                row.put("licence", stringValue(item.get("licence")));
                row.put("name", stringValue(item.get("name")));
                row.put("firstname", stringValue(item.get("firstname")));
                if (includeEvents) {
                    row.put("events", stringValue(item.get("events")));
                } else {
                    row.put("event", stringValue(item.get("event")));
                }
                double amount = item.get("amount") instanceof Number n ? n.doubleValue() : 0.0;
                row.put("amountFormatted", formatEuro(amount));
                rows.add(row);
            }
        }
        model.put(rowsKey, rows);
    }

    public static Map<String, Object> eventSummary(Event event) {
        Map<String, Object> row = new HashMap<>();
        if (event == null) {
            return row;
        }
        row.put("id", event.getId());
        row.put("name", event.getName() != null ? event.getName() : "");
        if (event.getStartDate() != null) {
            row.put("startDateText", String.valueOf(event.getStartDate()));
            row.put("hasStartDate", true);
        } else {
            row.put("startDateText", "");
            row.put("hasStartDate", false);
        }
        if (event.getEndDate() != null) {
            row.put("endDateText", String.valueOf(event.getEndDate()));
            row.put("hasEndDate", true);
        } else {
            row.put("endDateText", "");
            row.put("hasEndDate", false);
        }
        row.put("statusName", event.getStatus() != null ? event.getStatus().name() : "");
        return row;
    }

    public static Map<String, Object> collectionEventRow(Event event) {
        Map<String, Object> row = eventSummary(event);
        if (event != null && event.getStartDateAsDate() != null) {
            row.put("startDateText", event.getStartDateAsDate().toString());
            row.put("hasStartDate", true);
        }
        if (event != null && event.getStatus() != null) {
            String status = event.getStatus().name();
            row.put("statusMessageKey", "event.status." + status);
            row.put("statusIsDRAFT", "DRAFT".equals(status));
            row.put("statusIsACTIVE", "ACTIVE".equals(status));
            row.put("statusIsONGOING", "ONGOING".equals(status));
            row.put("statusIsCOMPLETED", "COMPLETED".equals(status));
            row.put("statusIsOther", !List.of("DRAFT", "ACTIVE", "ONGOING", "COMPLETED").contains(status));
        }
        return row;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String formatEuro(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private static String formatPercent(double percent) {
        return String.format(Locale.US, "%.2f", percent);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
