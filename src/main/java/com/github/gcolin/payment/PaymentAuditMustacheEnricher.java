package com.github.gcolin.payment;

import com.github.gcolin.platform.Messages;
import com.github.gcolin.registration.PlayerSubscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PaymentAuditMustacheEnricher {

    private PaymentAuditMustacheEnricher() {}

    public static void enrich(Map<String, Object> model, Messages messages) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mismatches = (List<Map<String, Object>>) model.get("mismatches");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (mismatches != null) {
            for (Map<String, Object> mismatch : mismatches) {
                rows.add(toRow(mismatch, messages));
            }
        }
        model.put("auditRows", rows);
        model.put("auditRowsEmpty", rows.isEmpty());
        model.put("mismatchCountText", messages.get("paymentAudit.mismatchCount", rows.size()));
    }

    private static Map<String, Object> toRow(Map<String, Object> mismatch, Messages messages) {
        Map<String, Object> row = new HashMap<>();
        Payment payment = mismatch.get("payment") instanceof Payment p ? p : null;
        long paymentCents = longValue(mismatch.get("paymentCents"));
        long expectedCents = longValue(mismatch.get("expectedCents"));
        long sumCents = longValue(mismatch.get("sumCents"));
        long gapCents = paymentCents - expectedCents;

        if (payment != null) {
            row.put("paymentId", payment.getId());
            row.put("hasPayment", true);
            if (payment.getUserEmail() != null) {
                row.put("userEmail", payment.getUserEmail());
            }
        }
        row.put("paymentAmountFormatted", formatEuro(paymentCents));
        row.put("expectedAmountFormatted", formatEuro(expectedCents));
        row.put("sumAmountFormatted", formatEuro(sumCents));
        row.put("gapAmountFormatted", formatEuro(Math.abs(gapCents)));

        if (gapCents > 0) {
            row.put("gapIsHigher", true);
        } else if (gapCents < 0) {
            row.put("gapIsLower", true);
        } else if (Boolean.TRUE.equals(mismatch.get("hasMissingAmount"))) {
            row.put("gapIsMissingAmount", true);
        } else {
            row.put("gapIsConsistent", true);
        }

        @SuppressWarnings("unchecked")
        List<PlayerSubscription> subs = mismatch.get("subs") instanceof List<?> list
                ? (List<PlayerSubscription>) list
                : List.of();
        List<Map<String, Object>> subRows = new ArrayList<>();
        for (PlayerSubscription sub : subs) {
            Map<String, Object> subRow = new HashMap<>();
            subRow.put("licence", sub.getNrFfe() != null ? sub.getNrFfe() : "");
            if (sub.getEvent() != null) {
                subRow.put("eventName", sub.getEvent().getName());
                subRow.put("hasEvent", true);
            } else {
                subRow.put("hasEvent", false);
            }
            if (sub.getAmountCents() != null) {
                subRow.put("amountFormatted", formatEuro(sub.getAmountCents()));
                subRow.put("hasAmount", true);
            } else {
                subRow.put("hasAmount", false);
            }
            subRows.add(subRow);
        }
        row.put("subs", subRows);
        row.put("hasSubs", !subRows.isEmpty());
        row.put("showFixForm", payment != null && !subRows.isEmpty());
        return row;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String formatEuro(long cents) {
        return String.format(Locale.US, "%.2f", cents / 100.0);
    }
}
