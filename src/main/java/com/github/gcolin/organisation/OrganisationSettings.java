package com.github.gcolin.organisation;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Packs typed organisation fields into {@code settings_json} and hydrates them back after load.
 * SQL columns keep only id / base_url / active / settings_json.
 */
public final class OrganisationSettings {

    private OrganisationSettings() {}

    /** Merges typed Java fields into {@code settings_json} (preserves invoice/ui/feature keys). */
    public static void pack(Organisation org) {
        if (org == null) {
            return;
        }
        JSONObject settings = parse(org.getSettingsJson());
        foldAliases(settings);
        putString(settings, "org.name", org.getName());
        putString(settings, OrgConfig.KEY_SUPER_USER_EMAIL, org.getSuperUserEmail());
        putString(settings, "stripe.public", org.getStripePublic());
        putString(settings, "stripe.secret", org.getStripeSecret());
        putString(settings, "stripe.keyprefix", org.getStripeKeyprefix());
        settings.put("stripe.simuled", String.valueOf(org.isStripeSimuled()));
        settings.put("stripe.card.events", String.valueOf(org.isStripeCardEvents()));
        settings.put("stripe.card.memberships", String.valueOf(org.isStripeCardMemberships()));
        settings.put("payment.transfer.events", String.valueOf(org.isPaymentTransferEvents()));
        settings.put("payment.transfer.memberships", String.valueOf(org.isPaymentTransferMemberships()));
        putString(settings, "oauth.clientId", firstNonBlank(org.getOauthClientId(), stringOrNull(settings, "oauth.clientId")));
        putString(
                settings,
                "oauth.clientSecret",
                firstNonBlank(org.getOauthClientSecret(), stringOrNull(settings, "oauth.clientSecret")));
        putString(
                settings,
                "oauth.authorizationUrl",
                firstNonBlank(org.getOauthAuthorizationUrl(), stringOrNull(settings, "oauth.authorizationUrl")));
        putString(settings, "oauth.tokenUrl", firstNonBlank(org.getOauthTokenUrl(), stringOrNull(settings, "oauth.tokenUrl")));
        putString(settings, "oauth.scope", firstNonBlank(org.getOauthScope(), stringOrNull(settings, "oauth.scope")));
        putString(
                settings, "oauth.logoutUrl", firstNonBlank(org.getOauthLogoutUrl(), stringOrNull(settings, "oauth.logoutUrl")));
        putString(
                settings,
                "oauth.accountUrl",
                firstNonBlank(org.getOauthAccountUrl(), stringOrNull(settings, "oauth.accountUrl")));
        putString(
                settings,
                "oauth.userinfoUrl",
                firstNonBlank(org.getOauthUserinfoUrl(), stringOrNull(settings, "oauth.userinfoUrl")));
        putString(settings, "mail.USER_NAME", org.getMailUserName());
        putString(settings, "mail.PASSWORD", org.getMailPassword());
        dropRedundantInvoiceKeys(settings);
        org.setSettingsJson(settings.isEmpty() ? null : settings.toString());
    }

    /** Fills typed Java fields from {@code settings_json}. */
    public static void unpack(Organisation org) {
        if (org == null) {
            return;
        }
        String normalized = normalizeJson(org.getSettingsJson());
        if (!jsonEquivalent(org.getSettingsJson(), normalized)) {
            org.setSettingsJson(normalized);
        }
        JSONObject settings = parse(org.getSettingsJson());
        org.setName(stringOrNull(settings, "org.name"));
        org.setSuperUserEmail(stringOrNull(settings, OrgConfig.KEY_SUPER_USER_EMAIL));
        org.setStripePublic(stringOrNull(settings, "stripe.public"));
        org.setStripeSecret(stringOrNull(settings, "stripe.secret"));
        org.setStripeKeyprefix(stringOrNull(settings, "stripe.keyprefix"));
        org.setStripeSimuled(boolOrDefault(settings, "stripe.simuled", false));
        org.setStripeCardEvents(boolOrDefault(settings, "stripe.card.events", true));
        org.setStripeCardMemberships(boolOrDefault(settings, "stripe.card.memberships", true));
        org.setPaymentTransferEvents(boolOrDefault(settings, "payment.transfer.events", true));
        org.setPaymentTransferMemberships(boolOrDefault(settings, "payment.transfer.memberships", true));
        org.setOauthClientId(stringOrNull(settings, "oauth.clientId"));
        org.setOauthClientSecret(stringOrNull(settings, "oauth.clientSecret"));
        org.setOauthAuthorizationUrl(stringOrNull(settings, "oauth.authorizationUrl"));
        org.setOauthTokenUrl(stringOrNull(settings, "oauth.tokenUrl"));
        org.setOauthScope(stringOrNull(settings, "oauth.scope"));
        org.setOauthLogoutUrl(stringOrNull(settings, "oauth.logoutUrl"));
        org.setOauthAccountUrl(stringOrNull(settings, "oauth.accountUrl"));
        org.setOauthUserinfoUrl(stringOrNull(settings, "oauth.userinfoUrl"));
        org.setMailUserName(stringOrNull(settings, "mail.USER_NAME"));
        org.setMailPassword(stringOrNull(settings, "mail.PASSWORD"));
    }

    /**
     * Folds legacy aliases ({@code keycloak.*}, {@code org.hosting.address}) and drops invoice seller
     * keys that duplicate {@code org.*}.
     */
    public static String normalizeJson(String settingsJson) {
        JSONObject settings = parse(settingsJson);
        foldAliases(settings);
        dropRedundantInvoiceKeys(settings);
        return settings.isEmpty() ? null : settings.toString();
    }

    /** True when both JSON blobs have the same keys/values (order-independent). */
    public static boolean jsonEquivalent(String left, String right) {
        JSONObject a = parse(left);
        JSONObject b = parse(right);
        return a.similar(b);
    }

    private static void foldAliases(JSONObject settings) {
        moveIfAbsent(settings, "keycloak.CLIENT_ID", "oauth.clientId");
        moveIfAbsent(settings, "keycloak.CLIENT_SECRET", "oauth.clientSecret");
        moveIfAbsent(settings, "keycloak.url", "oauth.authorizationUrl");
        removeKeyPrefix(settings, "keycloak.");
        moveIfAbsent(settings, "org.hosting.address", "org.address");
        settings.remove("org.hosting.address");
    }

    private static void dropRedundantInvoiceKeys(JSONObject settings) {
        removeIfEqual(settings, "invoice.seller.name", "org.name");
        removeIfEqual(settings, "invoice.seller.email", "org.email");
        removeIfEqual(settings, "invoice.seller.address1", "org.address");
        removeIfEqual(settings, "invoice.seller.website", "contact.url");
    }

    private static void moveIfAbsent(JSONObject settings, String from, String to) {
        String value = stringOrNull(settings, from);
        if (value == null) {
            return;
        }
        putIfAbsent(settings, to, value);
        settings.remove(from);
    }

    private static void removeKeyPrefix(JSONObject settings, String prefix) {
        for (String key : settings.keySet().toArray(String[]::new)) {
            if (key.startsWith(prefix)) {
                settings.remove(key);
            }
        }
    }

    private static void removeIfEqual(JSONObject settings, String key, String canonicalKey) {
        String value = stringOrNull(settings, key);
        String canonical = stringOrNull(settings, canonicalKey);
        if (value != null && canonical != null && value.equals(canonical)) {
            settings.remove(key);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    /**
     * Merges legacy SQL column values into settings when migrating. Does not overwrite keys already
     * present in {@code settings_json}.
     */
    public static String mergeLegacyColumns(String settingsJson, LegacyColumns legacy) {
        JSONObject settings = parse(settingsJson);
        putIfAbsent(settings, "org.name", legacy.name());
        putIfAbsent(settings, OrgConfig.KEY_SUPER_USER_EMAIL, legacy.superUserEmail());
        putIfAbsent(settings, "stripe.public", legacy.stripePublic());
        putIfAbsent(settings, "stripe.secret", legacy.stripeSecret());
        putIfAbsent(settings, "stripe.keyprefix", legacy.stripeKeyprefix());
        putIfAbsent(settings, "stripe.simuled", legacy.stripeSimuled() == null ? null : String.valueOf(legacy.stripeSimuled()));
        putIfAbsent(
                settings,
                "stripe.card.events",
                legacy.stripeCardEvents() == null ? null : String.valueOf(legacy.stripeCardEvents()));
        putIfAbsent(
                settings,
                "stripe.card.memberships",
                legacy.stripeCardMemberships() == null ? null : String.valueOf(legacy.stripeCardMemberships()));
        putIfAbsent(
                settings,
                "payment.transfer.events",
                legacy.paymentTransferEvents() == null ? null : String.valueOf(legacy.paymentTransferEvents()));
        putIfAbsent(
                settings,
                "payment.transfer.memberships",
                legacy.paymentTransferMemberships() == null
                        ? null
                        : String.valueOf(legacy.paymentTransferMemberships()));
        putIfAbsent(settings, "oauth.clientId", legacy.oauthClientId());
        putIfAbsent(settings, "oauth.clientSecret", legacy.oauthClientSecret());
        putIfAbsent(settings, "oauth.authorizationUrl", legacy.oauthAuthorizationUrl());
        putIfAbsent(settings, "oauth.tokenUrl", legacy.oauthTokenUrl());
        putIfAbsent(settings, "oauth.scope", legacy.oauthScope());
        putIfAbsent(settings, "oauth.logoutUrl", legacy.oauthLogoutUrl());
        putIfAbsent(settings, "oauth.accountUrl", legacy.oauthAccountUrl());
        putIfAbsent(settings, "oauth.userinfoUrl", legacy.oauthUserinfoUrl());
        putIfAbsent(settings, "mail.USER_NAME", legacy.mailUserName());
        putIfAbsent(settings, "mail.PASSWORD", legacy.mailPassword());
        foldAliases(settings);
        dropRedundantInvoiceKeys(settings);
        return settings.isEmpty() ? null : settings.toString();
    }

    public record LegacyColumns(
            String name,
            String superUserEmail,
            String stripePublic,
            String stripeSecret,
            String stripeKeyprefix,
            Boolean stripeSimuled,
            Boolean stripeCardEvents,
            Boolean stripeCardMemberships,
            Boolean paymentTransferEvents,
            Boolean paymentTransferMemberships,
            String oauthClientId,
            String oauthClientSecret,
            String oauthAuthorizationUrl,
            String oauthTokenUrl,
            String oauthScope,
            String oauthLogoutUrl,
            String oauthAccountUrl,
            String oauthUserinfoUrl,
            String mailUserName,
            String mailPassword) {}

    private static JSONObject parse(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void putString(JSONObject settings, String key, String value) {
        if (value == null || value.isBlank()) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
    }

    private static void putIfAbsent(JSONObject settings, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!settings.has(key) || settings.isNull(key) || settings.optString(key, "").isBlank()) {
            settings.put(key, value);
        }
    }

    private static String stringOrNull(JSONObject settings, String key) {
        if (!settings.has(key) || settings.isNull(key)) {
            return null;
        }
        String value = settings.optString(key, null);
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean boolOrDefault(JSONObject settings, String key, boolean defaultValue) {
        if (!settings.has(key) || settings.isNull(key)) {
            return defaultValue;
        }
        String raw = settings.optString(key, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
