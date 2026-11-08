package com.github.gcolin.payment;

import com.github.gcolin.membership.MembershipDisplay;
import com.github.gcolin.platform.Messages;
import com.github.gcolin.registration.SubscriptionDisplay;
import com.github.gcolin.registration.SubscriptionOptionDisplay;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PaymentMustacheEnricher {

    private PaymentMustacheEnricher() {}

    public static void enrichListPage(Map<String, Object> model, Messages messages) {
        @SuppressWarnings("unchecked")
        List<Payment> payments = model.get("payments") instanceof List<?> list ? (List<Payment>) list : List.of();
        @SuppressWarnings("unchecked")
        Map<String, String> licences = model.get("paymentSearchLicences") instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, String> names = model.get("paymentSearchNames") instanceof Map<?, ?> map
                ? (Map<String, String>) map
                : Map.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment payment : payments) {
            rows.add(toListRow(payment, licences, names));
        }
        model.put("paymentRows", rows);

        String search = model.get("search") instanceof String s ? s : "";
        model.put("hasSearch", !search.isBlank());
        if (!search.isBlank()) {
            model.put("searchEncoded", URLEncoder.encode(search, StandardCharsets.UTF_8));
        }

        Object totalItems = model.get("totalItems");
        Object currentPage = model.get("currentPage");
        Object totalPages = model.get("totalPages");
        if (totalItems != null && currentPage != null && totalPages != null) {
            model.put(
                    "paymentCountText",
                    messages.get("pagination.paymentCount", totalItems, currentPage, totalPages));
        }
    }

    public static void enrichEditPage(Map<String, Object> model, Messages messages) {
        Payment payment = model.get("payment") instanceof Payment p ? p : null;
        boolean donationMode = Boolean.TRUE.equals(model.get("donationMode"))
                || (payment != null && payment.isDonation());
        model.put("donationMode", donationMode);

        PaymentStatus[] statuses = model.get("paymentStatuses") instanceof PaymentStatus[] arr
                ? arr
                : PaymentStatus.values();
        PaymentType[] types = model.get("paymentTypes") instanceof PaymentType[] arr
                ? arr
                : PaymentType.values();

        PaymentStatus selectedStatus = payment != null ? payment.getStatus() : null;
        PaymentType selectedType = payment != null ? payment.getType() : null;
        model.put("paymentStatusOptions", enumOptions(statuses, selectedStatus, "payment.status."));
        model.put("paymentTypeOptions", enumOptions(types, selectedType, "payment.type."));

        boolean hasId = payment != null && payment.getId() != null;
        model.put("paymentHasId", hasId);
        model.put(
                "showReceiptActions",
                hasId && payment.getStatus() == PaymentStatus.PAID);
        model.put("successIsReceiptSent", "receiptSent".equals(model.get("success")));

        if (hasId) {
            model.put(
                    "paymentEditTitle",
                    donationMode
                            ? messages.get("payment.editDonationTitle", payment.getId())
                            : messages.get("payment.editTitle", payment.getId()));
        } else {
            model.put(
                    "paymentEditTitle",
                    donationMode ? messages.get("action.newDonation") : messages.get("action.newPayment"));
        }

        @SuppressWarnings("unchecked")
        List<SubscriptionDisplay> subscriptionDisplays =
                model.get("subscriptionDisplays") instanceof List<?> list
                        ? (List<SubscriptionDisplay>) list
                        : List.of();
        List<Map<String, Object>> subRows = new ArrayList<>();
        for (SubscriptionDisplay display : subscriptionDisplays) {
            subRows.add(toSubscriptionRow(display));
        }
        model.put("subscriptionDisplays", subRows);
        model.put("hasSubscriptionDisplays", !subRows.isEmpty());

        @SuppressWarnings("unchecked")
        List<SubscriptionOptionDisplay> optionDisplays =
                model.get("optionDisplays") instanceof List<?> list
                        ? (List<SubscriptionOptionDisplay>) list
                        : List.of();
        List<Map<String, Object>> optionRows = new ArrayList<>();
        for (SubscriptionOptionDisplay display : optionDisplays) {
            optionRows.add(toOptionRow(display));
        }
        model.put("optionDisplays", optionRows);
        model.put("hasOptionDisplays", !optionRows.isEmpty());

        @SuppressWarnings("unchecked")
        List<MembershipDisplay> membershipDisplays =
                model.get("membershipDisplays") instanceof List<?> list
                        ? (List<MembershipDisplay>) list
                        : List.of();
        List<Map<String, Object>> membershipRows = new ArrayList<>();
        for (MembershipDisplay display : membershipDisplays) {
            membershipRows.add(toMembershipRow(display));
        }
        model.put("membershipDisplays", membershipRows);
        model.put("hasMembershipDisplays", !membershipRows.isEmpty());
    }

    private static Map<String, Object> toListRow(
            Payment payment, Map<String, String> licences, Map<String, String> names) {
        Map<String, Object> row = new HashMap<>();
        row.put("payment", payment);
        String id = payment.getId() != null ? String.valueOf(payment.getId()) : "";
        row.put("licences", licences.getOrDefault(id, ""));
        row.put("names", names.getOrDefault(id, ""));
        if (payment.getStatus() != null) {
            row.put("statusMessageKey", "payment.status." + payment.getStatus());
        }
        if (payment.getType() != null) {
            row.put("typeMessageKey", "payment.type." + payment.getType());
        }
        Double amount = payment.getAmount();
        if (amount == null && payment.getAmountCents() != null) {
            amount = payment.getAmountCents() / 100.0;
        }
        row.put(
                "amountFormatted",
                amount != null ? String.format(Locale.FRANCE, "%.2f", amount) : "0,00");
        if (payment.getCreatedAt() != null) {
            row.put("createdAtText", payment.getCreatedAt().toString());
        }
        if (payment.getUpdatedAt() != null) {
            row.put("updatedAtText", payment.getUpdatedAt().toString());
        }
        return row;
    }

    private static List<Map<String, Object>> enumOptions(
            Enum<?>[] values, Enum<?> selected, String keyPrefix) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Enum<?> value : values) {
            Map<String, Object> option = new HashMap<>();
            option.put("value", value.name());
            option.put("labelKey", keyPrefix + value.name());
            option.put("selected", selected != null && selected == value);
            options.add(option);
        }
        return options;
    }

    private static Map<String, Object> toSubscriptionRow(SubscriptionDisplay display) {
        Map<String, Object> row = new HashMap<>();
        row.put("subscriptionId", display.getSubscriptionId());
        putIfPresent(row, "editLink", display.getEditLink());
        putIfPresent(row, "playerName", display.getPlayerName());
        putIfPresent(row, "eventName", display.getEventName());
        putIfPresent(row, "eventLink", display.getEventLink());
        row.put(
                "hasPlayerOrEventName",
                (display.getPlayerName() != null && !display.getPlayerName().isBlank())
                        || (display.getEventName() != null && !display.getEventName().isBlank()));
        return row;
    }

    private static Map<String, Object> toOptionRow(SubscriptionOptionDisplay display) {
        Map<String, Object> row = new HashMap<>();
        row.put("optionId", display.getOptionId());
        putIfPresent(row, "description", display.getDescription());
        putIfPresent(row, "editLink", display.getEditLink());
        putIfPresent(row, "playerName", display.getPlayerName());
        putIfPresent(row, "eventName", display.getEventName());
        putIfPresent(row, "eventLink", display.getEventLink());
        if (display.getAmountCents() != null) {
            row.put("amountEuros", display.getAmountCents() / 100.0);
        }
        if (display.getStatus() != null && !display.getStatus().isBlank()) {
            row.put("statusMessageKey", "playerStatus." + display.getStatus());
        }
        row.put(
                "hasPlayerOrEventName",
                (display.getPlayerName() != null && !display.getPlayerName().isBlank())
                        || (display.getEventName() != null && !display.getEventName().isBlank()));
        return row;
    }

    private static Map<String, Object> toMembershipRow(MembershipDisplay display) {
        Map<String, Object> row = new HashMap<>();
        row.put("membershipId", display.getMembershipId());
        putIfPresent(row, "editLink", display.getEditLink());
        putIfPresent(row, "memberName", display.getMemberName());
        if (display.getAmountCents() != null) {
            row.put("amountEuros", display.getAmountCents() / 100.0);
        }
        if (display.getStatus() != null && !display.getStatus().isBlank()) {
            row.put("statusMessageKey", "clubRegister.membershipStatus." + display.getStatus());
        }
        return row;
    }

    private static void putIfPresent(Map<String, Object> row, String key, String value) {
        if (value != null && !value.isBlank()) {
            row.put(key, value);
        }
    }
}
