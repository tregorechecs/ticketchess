package com.github.gcolin.platform;

import java.util.LinkedHashMap;
import java.util.Map;

/** Precomputes Mustache-friendly flags and cfg field map for organisation admin settings tabs. */
public final class MustacheOrgConfigView {

    private MustacheOrgConfigView() {}

    @SuppressWarnings("unchecked")
    public static void enrich(Map<String, Object> model) {
        Object cfgObj = model.get("cfg");
        if (!(cfgObj instanceof Map<?, ?> cfgMap)) {
            return;
        }
        Map<String, String> cfg = (Map<String, String>) cfgMap;

        Map<String, String> cfgu = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : cfg.entrySet()) {
            cfgu.put(cfgKeyToField(entry.getKey()), nullToEmpty(entry.getValue()));
        }
        model.put("cfgu", cfgu);

        Object tab = model.get("tab");
        if (tab instanceof String tabName && !tabName.isBlank()) {
            model.put("tabIs" + tabName, true);
        }

        Map<String, Boolean> secrets = null;
        Object secretsObj = model.get("secretsConfigured");
        if (secretsObj instanceof Map<?, ?> secretsMap) {
            secrets = (Map<String, Boolean>) secretsMap;
        }
        model.put("stripeSecretConfigured", secretConfigured(secrets, "stripe.secret"));
        model.put("oauthClientSecretConfigured", secretConfigured(secrets, "oauth.clientSecret"));
        model.put("jwtKeyConfigured", secretConfigured(secrets, "jwt.key"));
        model.put("mailPasswordConfigured", secretConfigured(secrets, "mail.PASSWORD"));
        model.put("dbPassConfigured", secretConfigured(secrets, "db.pass"));

        boolean featureMemberships = cfgTriStateDefaultTrue(cfg, "feature.memberships");
        boolean featureEvents = cfgTriStateDefaultTrue(cfg, "feature.events");
        model.put("stripeSimuled", cfgIsTrue(cfg, "stripe.simuled", false));
        model.put("featureMemberships", featureMemberships);
        model.put("featureEvents", featureEvents);
        model.put("featurePayments", cfgTriStateDefaultTrue(cfg, "feature.payments"));
        model.put(
                "stripeCardEvents",
                featureEvents && cfgTriStateDefaultTrue(cfg, "stripe.card.events"));
        model.put(
                "stripeCardMemberships",
                featureMemberships && cfgTriStateDefaultTrue(cfg, "stripe.card.memberships"));
        model.put(
                "paymentTransferEvents",
                featureEvents && cfgTriStateDefaultTrue(cfg, "payment.transfer.events"));
        model.put(
                "paymentTransferMemberships",
                featureMemberships && cfgTriStateDefaultTrue(cfg, "payment.transfer.memberships"));
        model.put("dbTypePostgres", "postgres".equals(String.valueOf(cfg.get("db.type"))));
    }

    private static boolean secretConfigured(Map<String, Boolean> secrets, String key) {
        if (secrets == null) {
            return false;
        }
        Boolean configured = secrets.get(key);
        return configured != null && configured;
    }

    /** Checkbox defaults to checked when value is null, blank, or {@code true}. */
    private static boolean cfgTriStateDefaultTrue(Map<String, String> cfg, String key) {
        String value = cfg.get(key);
        if (value == null || value.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private static boolean cfgIsTrue(Map<String, String> cfg, String key, boolean defaultValue) {
        String value = cfg.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private static String cfgKeyToField(String key) {
        return key.replace('.', '_');
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
