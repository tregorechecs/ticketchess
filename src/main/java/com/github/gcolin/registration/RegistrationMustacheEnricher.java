package com.github.gcolin.registration;

import com.github.gcolin.payment.Payment;
import com.github.gcolin.platform.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegistrationMustacheEnricher {

    private RegistrationMustacheEnricher() {}

    public static void enrichPlayerSubscriptionAdminPage(Map<String, Object> model, Messages messages) {
        if (model.get("replaced") != null) {
            model.put("showReplaceResult", true);
            int replacedCount = model.get("replaced") instanceof Number n ? n.intValue() : 0;
            int failedCount = model.get("failed") instanceof Number n ? n.intValue() : 0;
            model.put("replaceResultHasFailures", failedCount > 0);
            model.put("replaceResultText", messages.get("playersub.replaceResult", replacedCount, failedCount));
        }
        if (model.get("deleted") != null) {
            model.put("showDeleteResult", true);
            int deletedCount = model.get("deleted") instanceof Number n ? n.intValue() : 0;
            int deleteFailedCount = model.get("deleteFailed") instanceof Number n ? n.intValue() : 0;
            model.put("deleteResultHasFailures", deleteFailedCount > 0);
            model.put("deleteResultText", messages.get("playersub.deleteResult", deletedCount, deleteFailedCount));
        }
    }

    public static void enrichSubEdit(Map<String, Object> model) {
        PlayerSubscription sub = model.get("sub") instanceof PlayerSubscription s ? s : null;
        if (sub == null) {
            return;
        }
        if (sub.getStatus() != null && !model.containsKey("status")) {
            model.put("status", sub.getStatus().name());
        }
        model.put("amountCentsValue", sub.getAmountCents() != null ? sub.getAmountCents() : 0L);
        Payment payment = sub.getPayment();
        if (payment != null && payment.getId() != null) {
            model.put("hasPayment", true);
            model.put("paymentId", payment.getId());
        } else {
            model.put("hasPayment", false);
        }

        @SuppressWarnings("unchecked")
        List<PlayerSubscriptionOption> options = model.get("options") instanceof List<?> list
                ? (List<PlayerSubscriptionOption>) list
                : List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlayerSubscriptionOption option : options) {
            rows.add(toOptionRow(option));
        }
        model.put("optionRows", rows);
        model.put("hasOptions", !rows.isEmpty());
    }

    public static void enrichSubOptionEdit(Map<String, Object> model) {
        PlayerSubscriptionOption option =
                model.get("option") instanceof PlayerSubscriptionOption o ? o : null;
        if (option != null) {
            if (option.getStatus() != null && !model.containsKey("status")) {
                model.put("status", option.getStatus().name());
            }
            model.put("amountCentsValue", option.getAmountCents() != null ? option.getAmountCents() : 0L);
            Payment payment = option.getPayment();
            if (payment != null && payment.getId() != null) {
                model.put("hasPaymentLink", true);
                model.put("paymentId", payment.getId());
            }
        } else {
            if (!model.containsKey("status")) {
                model.put("status", PlayerSubscriptionOptionStatus.NOT_PAID.name());
            }
            model.put("amountCentsValue", 0L);
            model.put("option", Map.of("description", "", "id", ""));
        }
    }

    private static Map<String, Object> toOptionRow(PlayerSubscriptionOption option) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", option.getId());
        row.put("description", option.getDescription() != null ? option.getDescription() : "");
        long cents = option.getAmountCents() != null ? option.getAmountCents() : 0L;
        row.put("amountEuros", String.format(Locale.FRANCE, "%.2f", cents / 100.0));
        if (option.getStatus() != null) {
            row.put("statusMessageKey", "playerStatus." + option.getStatus().name());
        }
        return row;
    }
}
