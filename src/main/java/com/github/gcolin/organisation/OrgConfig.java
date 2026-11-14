package com.github.gcolin.organisation;

import java.net.URI;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Property-style view of an {@link Organisation}.
 * SQL columns are only {@code id}/{@code base_url}/{@code active}/{@code settings_json};
 * typed keys (name, stripe, oauth, mail, …) live in {@code settings_json} via {@link OrganisationSettings}.
 */
public class OrgConfig {

    public static final String KEY_SUPER_USER_EMAIL = "org.superUserEmail";

    private static final Set<String> GLOBAL_KEYS =
            Set.of("jwt.key", "testmode", "admins", "pendingQueueOffset", "auth.USER_EMAIL", "auth.USER_NAME");
    private static final List<String> GLOBAL_PREFIXES = List.of("db.");
    public static final String FEATURE_MEMBERSHIPS = "feature.memberships";
    public static final String FEATURE_EVENTS = "feature.events";
    public static final String FEATURE_PAYMENTS = "feature.payments";
    /** Enabled plugin ids (JSON array in {@code settings_json}); absent means derived from {@code feature.*}. */
    public static final String KEY_PLUGINS = "plugins";

    /** Public source repository URL (AGPL). Not configurable per organisation. */
    public static final String SOURCE_URL = "https://github.com/gcolin/ticketchess";

    private static final Set<String> SCOPED_KEYS =
            Set.of("title", "contact.url", "membership.notif.emails", KEY_PLUGINS);
    private static final List<String> SCOPED_PREFIXES =
            List.of("invoice.", "org.", "keycloak.", "stripe.", "oauth.", "ui.", "feature.");
    private static final Set<String> SECRET_KEYS = Set.of("stripe.secret", "oauth.clientSecret", "mail.PASSWORD");

    private record Accessor(Function<Organisation, String> get, BiConsumer<Organisation, String> set) {}

    private static final Map<String, Accessor> TYPED = new LinkedHashMap<>();

    static {
        typed("baseurl", Organisation::getBaseUrl, (o, v) -> {
            if (!isBlank(v)) {
                o.setBaseUrl(normalizeBaseUrl(v));
            }
        });
        typed("org.name", Organisation::getName, (o, v) -> {
            if (!isBlank(v)) {
                o.setName(v.trim());
            }
        });
        typed(KEY_SUPER_USER_EMAIL, Organisation::getSuperUserEmail, (o, v) -> {
            if (!isBlank(v)) {
                o.setSuperUserEmail(v.trim().toLowerCase(Locale.ROOT));
            }
        });
        typed("stripe.public", Organisation::getStripePublic, Organisation::setStripePublic);
        typed("stripe.secret", Organisation::getStripeSecret, Organisation::setStripeSecret);
        typed("stripe.keyprefix", Organisation::getStripeKeyprefix, Organisation::setStripeKeyprefix);
        typedBool("stripe.simuled", Organisation::isStripeSimuled, Organisation::setStripeSimuled);
        typedBool("stripe.card.events", Organisation::isStripeCardEvents, Organisation::setStripeCardEvents);
        typedBool(
                "stripe.card.memberships",
                Organisation::isStripeCardMemberships,
                Organisation::setStripeCardMemberships);
        typedBool("payment.transfer.events", Organisation::isPaymentTransferEvents, Organisation::setPaymentTransferEvents);
        typedBool(
                "payment.transfer.memberships",
                Organisation::isPaymentTransferMemberships,
                Organisation::setPaymentTransferMemberships);
        typed("oauth.clientId", Organisation::getOauthClientId, Organisation::setOauthClientId);
        typed("oauth.clientSecret", Organisation::getOauthClientSecret, Organisation::setOauthClientSecret);
        typed("oauth.authorizationUrl", Organisation::getOauthAuthorizationUrl, Organisation::setOauthAuthorizationUrl);
        typed("oauth.tokenUrl", Organisation::getOauthTokenUrl, Organisation::setOauthTokenUrl);
        typed("oauth.scope", Organisation::getOauthScope, Organisation::setOauthScope);
        typed("oauth.logoutUrl", Organisation::getOauthLogoutUrl, Organisation::setOauthLogoutUrl);
        typed("oauth.accountUrl", Organisation::getOauthAccountUrl, Organisation::setOauthAccountUrl);
        typed("oauth.userinfoUrl", Organisation::getOauthUserinfoUrl, Organisation::setOauthUserinfoUrl);
        typed("mail.USER_NAME", Organisation::getMailUserName, Organisation::setMailUserName);
        typed("mail.PASSWORD", Organisation::getMailPassword, Organisation::setMailPassword);
    }

    private final Organisation organisation;

    public OrgConfig(Organisation organisation) {
        this.organisation = organisation;
    }

    private static void typed(String key, Function<Organisation, String> get, BiConsumer<Organisation, String> set) {
        TYPED.put(key, new Accessor(get, set));
    }

    private static void typedBool(
            String key, Function<Organisation, Boolean> get, BiConsumer<Organisation, Boolean> set) {
        TYPED.put(
                key,
                new Accessor(o -> String.valueOf(get.apply(o)), (o, v) -> set.accept(o, Boolean.parseBoolean(v))));
    }

    public Organisation organisation() {
        return organisation;
    }

    public static Set<String> typedKeys() {
        return Collections.unmodifiableSet(TYPED.keySet());
    }

    /** Keys owned by the organisation: they never fall back to the platform {@code params.properties}. */
    public static boolean isOrgScoped(String key) {
        if (key == null || isPlatformGlobal(key)) {
            return false;
        }
        if (TYPED.containsKey(key) || SCOPED_KEYS.contains(key)) {
            return true;
        }
        for (String prefix : SCOPED_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Platform-only keys stored in {@code params.properties} (never on {@link Organisation}).
     * {@code baseurl} is the platform fallback; each org keeps its own URL on {@link Organisation#getBaseUrl()}.
     */
    public static boolean isPlatformGlobal(String key) {
        if (key == null) {
            return false;
        }
        if (GLOBAL_KEYS.contains(key) || "baseurl".equals(key)) {
            return true;
        }
        return isGlobalPrefix(key);
    }

    /**
     * Keys for organisation settings tabs club, invoice and stripe (including mail on the club tab).
     * Stored on {@link Organisation} / {@code settings_json}. OAuth and system keys are edited under
     * {@code /admin/org} (OAuth may still be stored on the current organisation via typed fields).
     */
    public static boolean isClubInvoiceStripeSettingsKey(String key) {
        if (key == null || isPlatformGlobal(key)) {
            return false;
        }
        if (key.startsWith("oauth.") || key.startsWith("keycloak.")) {
            return false;
        }
        if (TYPED.containsKey(key) || SCOPED_KEYS.contains(key)) {
            return true;
        }
        return key.startsWith("invoice.")
                || key.startsWith("org.")
                || key.startsWith("stripe.")
                || key.startsWith("mail.")
                || key.startsWith("ui.");
    }

    public static boolean isSecret(String key) {
        return SECRET_KEYS.contains(key);
    }

    /**
     * Feature toggles default to enabled when unset/blank. Only an explicit {@code false} disables.
     */
    public static boolean isFeatureEnabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(raw.trim());
    }

    public boolean isFeatureEnabledKey(String key) {
        return isFeatureEnabled(getProperty(key));
    }

    /**
     * Plugins enabled for an organisation. An explicit {@code plugins} list wins; otherwise they derive from
     * {@code feature.*}: events/membership/payments follow their flag, desk follows events, and the
     * email, player-index and custom-player plugins are on.
     */
    public static Set<String> resolveEnabledPlugins(Function<String, String> lookup) {
        String raw = lookup.apply(KEY_PLUGINS);
        if (raw != null) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(parsePlugins(raw)));
        }
        boolean events = isFeatureEnabled(lookup.apply(FEATURE_EVENTS));
        boolean memberships = isFeatureEnabled(lookup.apply(FEATURE_MEMBERSHIPS));
        boolean payments = isFeatureEnabled(lookup.apply(FEATURE_PAYMENTS));
        Set<String> enabled = new LinkedHashSet<>();
        for (String id : PluginIds.KNOWN) {
            boolean on = switch (id) {
                case PluginIds.EVENTS, PluginIds.DESK -> events;
                case PluginIds.MEMBERSHIP -> memberships;
                case PluginIds.PAYMENTS -> payments;
                default -> true;
            };
            if (on) {
                enabled.add(id);
            }
        }
        return Collections.unmodifiableSet(enabled);
    }

    public Set<String> enabledPlugins() {
        return resolveEnabledPlugins(this::getProperty);
    }

    public boolean isPluginEnabled(String pluginId) {
        return enabledPlugins().contains(pluginId);
    }

    /** Stores an explicit plugin list ({@code null} removes it, restoring the {@code feature.*} derivation). */
    public void setPlugins(Collection<String> pluginIds) {
        JSONObject settings = settings();
        if (pluginIds == null) {
            settings.remove(KEY_PLUGINS);
        } else {
            settings.put(KEY_PLUGINS, new JSONArray(new ArrayList<>(new LinkedHashSet<>(pluginIds))));
        }
        organisation.setSettingsJson(settings.isEmpty() ? null : settings.toString());
    }

    static List<String> parsePlugins(String csv) {
        List<String> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String pluginsCsv(JSONObject settings) {
        Object value = settings.opt(KEY_PLUGINS);
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof JSONArray array) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                ids.add(array.optString(i, ""));
            }
            return String.join(",", ids);
        }
        return value.toString();
    }

    public String getProperty(String key) {
        Accessor accessor = TYPED.get(key);
        if (accessor != null) {
            return accessor.get().apply(organisation);
        }
        JSONObject settings = settings();
        if (KEY_PLUGINS.equals(key)) {
            return pluginsCsv(settings);
        }
        if (settings.has(key) && !settings.isNull(key)) {
            return settings.optString(key, null);
        }
        if ("title".equals(key)) {
            return organisation.getName();
        }
        return null;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Whether the key is stored on this organisation (typed column or {@code settings_json}),
     * ignoring display fallbacks such as {@code title} → {@link Organisation#getName()}.
     */
    public boolean isExplicitlySet(String key) {
        if (key == null) {
            return false;
        }
        Accessor accessor = TYPED.get(key);
        if (accessor != null) {
            if (isTypedBoolKey(key)) {
                // Booleans always stringify; cannot distinguish defaults — allow one-shot platform migrate.
                return false;
            }
            String value = accessor.get().apply(organisation);
            return value != null && !value.isBlank();
        }
        JSONObject settings = settings();
        return settings.has(key) && !settings.isNull(key);
    }

    private static boolean isTypedBoolKey(String key) {
        return key != null
                && (key.equals("stripe.simuled")
                        || key.equals("stripe.card.events")
                        || key.equals("stripe.card.memberships")
                        || key.equals("payment.transfer.events")
                        || key.equals("payment.transfer.memberships"));
    }

    public void setProperty(String key, String value) {
        Accessor accessor = TYPED.get(key);
        if (accessor != null) {
            accessor.set().accept(organisation, value);
            return;
        }
        if (KEY_PLUGINS.equals(key)) {
            setPlugins(value == null ? null : parsePlugins(value));
            return;
        }
        JSONObject settings = settings();
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
        organisation.setSettingsJson(settings.isEmpty() ? null : settings.toString());
    }

    /** All configured values (typed columns and settings), optionally without secrets. */
    public Map<String, String> toMap(boolean includeSecrets) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, Accessor> entry : TYPED.entrySet()) {
            if (!includeSecrets && SECRET_KEYS.contains(entry.getKey())) {
                continue;
            }
            String value = entry.getValue().get().apply(organisation);
            if (value != null) {
                map.put(entry.getKey(), value);
            }
        }
        JSONObject settings = settings();
        for (String key : settings.keySet()) {
            if (!settings.isNull(key) && (includeSecrets || !SECRET_KEYS.contains(key))) {
                map.put(key, KEY_PLUGINS.equals(key) ? pluginsCsv(settings) : settings.optString(key, ""));
            }
        }
        return map;
    }

    public int getId() {
        return organisation.getId() == null ? OrgScope.DEFAULT_ORG_ID : organisation.getId();
    }

    public String getName() {
        return organisation.getName();
    }

    public String getBaseUrl() {
        return organisation.getBaseUrl();
    }

    public String getSuperUserEmail() {
        return organisation.getSuperUserEmail();
    }

    public String getStripePublic() {
        return organisation.getStripePublic();
    }

    public String getStripeSecret() {
        return organisation.getStripeSecret();
    }

    public String getOauthClientId() {
        return organisation.getOauthClientId();
    }

    public String getOauthClientSecret() {
        return organisation.getOauthClientSecret();
    }

    public String getMailUserName() {
        return organisation.getMailUserName();
    }

    public String getMailPassword() {
        return organisation.getMailPassword();
    }

    /** Host (with explicit port when present) of the organisation base URL, lower-cased. */
    public String getHost() {
        return hostOf(organisation.getBaseUrl());
    }

    private JSONObject settings() {
        String json = organisation.getSettingsJson();
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** Builds a new (unsaved) organisation from platform properties: typed keys to columns, the rest to JSON. */
    public static Organisation seed(Properties properties) {
        Organisation org = new Organisation();
        String baseUrl = properties.getProperty("baseurl", "http://localhost:8080");
        org.setBaseUrl(normalizeBaseUrl(isBlank(baseUrl) ? "http://localhost:8080" : baseUrl));
        String name = firstNonBlank(properties.getProperty("org.name"), properties.getProperty("title"), "TicketChess");
        org.setName(name);
        org.setSuperUserEmail(firstAdminEmail(properties));
        OrgConfig config = new OrgConfig(org);
        for (String key : properties.stringPropertyNames()) {
            if (GLOBAL_KEYS.contains(key) || isGlobalPrefix(key) || "baseurl".equals(key) || "org.name".equals(key)) {
                continue;
            }
            String value = properties.getProperty(key);
            if (value != null) {
                config.setProperty(key, value);
            }
        }
        // Club / stripe / mail live on the organisation row. Under testmode, fill blanks in the DB
        // so params.properties can stay platform-only after the one-shot migrate.
        if (Boolean.parseBoolean(properties.getProperty("testmode", "false"))) {
            applyTestModeDefaults(org, properties);
        }
        return org;
    }

    /**
     * Integration-test defaults written on the organisation entity (not left in {@code params.properties}).
     * Does not override values already set from an explicit seed key.
     */
    static void applyTestModeDefaults(Organisation org, Properties properties) {
        OrgConfig config = new OrgConfig(org);
        String admin = firstAdminEmail(properties);
        if ("TicketChess".equals(org.getName())
                && isBlank(properties.getProperty("org.name"))
                && isBlank(properties.getProperty("title"))) {
            org.setName("Ticket Chess Test");
        }
        if (!properties.containsKey("stripe.simuled")
                && !properties.containsKey("stripe.simulated")
                && isBlank(org.getStripePublic())) {
            org.setStripeSimuled(true);
        }
        // title falls back to org name in getProperty — use isExplicitlySet for settings_json.
        if (!config.isExplicitlySet("title") && !isBlank(org.getName())) {
            config.setProperty("title", org.getName());
        }
        putIfBlank(config, "membership.notif.emails", admin);
        putIfBlank(config, "org.email", admin);
        putIfBlank(config, "org.address", "1 rue Test, 00000 Ville, France");
        putIfBlank(config, "contact.url", "http://localhost:8080/contact");
        putIfBlank(config, "mail.USER_NAME", admin);
    }

    private static void putIfBlank(OrgConfig config, String key, String value) {
        if (isBlank(config.getProperty(key)) && !isBlank(value)) {
            config.setProperty(key, value);
        }
    }

    private static String firstAdminEmail(Properties properties) {
        String raw = firstNonBlank(properties.getProperty("admins"), properties.getProperty("auth.USER_EMAIL"));
        if (raw != null) {
            for (String part : raw.split(",")) {
                if (!part.isBlank()) {
                    return part.trim().toLowerCase(Locale.ROOT);
                }
            }
        }
        return "test@test.com";
    }

    static boolean isGlobalPrefix(String key) {
        for (String prefix : GLOBAL_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeBaseUrl(String value) {
        String url = value == null ? "" : value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getHost() == null) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
