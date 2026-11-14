package com.github.gcolin.platform;

import com.github.gcolin.organisation.OrgConfig;
import com.github.gcolin.organisation.OrgUiStyle;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrganisationDao;
import com.github.gcolin.organisation.OrganisationFiles;
import com.github.gcolin.organisation.OrgProperties;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.platform.plugin.PluginIds;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import jakarta.servlet.ServletContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

    static final String DEFAULT_JWT_KEY = "9f3b2a8c5d4e7f1b2c9a0d6e3f1b4c7a8d2e5f9c1b0a3d6e4f7c8b9a2d1e6f3b";
    /** HS256 requires jwt.key of at least 32 UTF-8 bytes (RFC 7518). */
    public static final MacAlgorithm JWT_ALGORITHM = Jwts.SIG.HS256;
    static final int JWT_KEY_MIN_BYTES = 32;
    public static final Set<String> SECRET_KEYS = Set.of(
            "stripe.secret", "oauth.clientSecret", "jwt.key", "mail.PASSWORD", "db.pass", "keycloak.CLIENT_SECRET");

    private Properties properties = new OrgProperties();
    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private ServletContext context;

    private Set<String> admins;

    private SecretKey keys;

    private boolean testMode;

    private int pendingQueueOffset;

    private Page page = new Page();

    private String sanitizeProperties(Properties props) {
        StringBuilder result = new StringBuilder();
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (key.toLowerCase().contains("password")
                    || key.toLowerCase().contains("secret")
                    || key.toLowerCase().contains("key")) {
                value = "[REDACTED]";
            }
            result.append(key).append("=").append(value).append("\n");
        }
        return result.toString();
    }

    public String getConfigDir() {
        String configDir = System.getenv("CONFIG_DIR");
        if (configDir == null || configDir.isBlank()) {
            configDir = System.getProperty("CONFIG_DIR");
        }
        return configDir == null || configDir.isBlank() ? "." : configDir;
    }

    public Path getConfigFile() {
        return Path.of(getConfigDir(), "params.properties");
    }

    public void init(ServletContext context) {
        this.context = context;
        Path configFile = getConfigFile();
        if (Files.exists(configFile)) {
            logger.info("Read config file: {}", configFile);
            try (Reader fin = new InputStreamReader(new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                properties.load(fin);
            } catch (IOException e) {
                logger.error("cannot read config file", e);
            }
        } else {
            logger.info("No params.properties at {}; using built-in defaults", configFile);
            if (!properties.containsKey("testmode")) {
                properties.put("testmode", "true");
            }
        }

        if (!properties.containsKey("auth.USER_EMAIL")) {
            properties.put("auth.USER_EMAIL", "test@test.com");
        }
        if (!properties.containsKey("auth.USER_NAME")) {
            properties.put("auth.USER_NAME", "Test");
        }
        if (!properties.containsKey("title")) {
            properties.put("title", "Event Test");
        }
        if (!properties.containsKey("contact.url")) {
            properties.put("contact.url", "http://localhost:8080");
        }
        if (!properties.containsKey("baseurl")) {
            properties.put("baseurl", "http://localhost:8080");
        }

        logger.info(sanitizeProperties(properties));
        applyRuntime();
        validateSecurity();
    }

    public void updateProperties(Map<String, String> updates) throws IOException {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        Map<String, String> platformUpdates = new LinkedHashMap<>();
        Map<String, String> orgUpdates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (SECRET_KEYS.contains(key) && value.isBlank()) {
                continue;
            }
            if (shouldWriteToCurrentOrg(key)) {
                orgUpdates.put(key, value);
            } else {
                platformUpdates.put(key, value);
            }
        }
        if (!orgUpdates.isEmpty()) {
            persistOrgUpdates(orgUpdates);
        }
        if (platformUpdates.isEmpty()) {
            applyRuntime();
            return;
        }
        Properties previous = snapshotProperties();
        for (Map.Entry<String, String> entry : platformUpdates.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        applyRuntime();
        try {
            validateSecurity();
        } catch (IllegalStateException e) {
            restoreProperties(previous);
            throw new IOException(e.getMessage(), e);
        }
        PropertiesFileUpdater.update(getConfigFile(), platformUpdates, SECRET_KEYS);
    }

    private static boolean shouldWriteToCurrentOrg(String key) {
        // Org-scoped keys (club/invoice/stripe/oauth/mail/…) never go to params.properties.
        return OrgConfig.isOrgScoped(key);
    }

    private void persistOrgUpdates(Map<String, String> orgUpdates) throws IOException {
        RequestContext ctx = RequestContext.current();
        Organisation current = OrgScope.currentOrg();
        if (ctx == null || current == null || current.getId() == null) {
            throw new IOException("No organisation in scope to save settings");
        }
        OrganisationDao organisationDao = ctx.organisationDao();
        Organisation managed = organisationDao.find(current.getId());
        if (managed == null) {
            throw new IOException("Organisation not found: " + current.getId());
        }
        OrgConfig orgConfig = new OrgConfig(managed);
        for (Map.Entry<String, String> entry : orgUpdates.entrySet()) {
            orgConfig.setProperty(entry.getKey(), entry.getValue());
        }
        organisationDao.merge(managed);
        ctx.setOrganisation(managed);
        if (AppContext.get() != null && AppContext.get().organisationResolver() != null) {
            AppContext.get().organisationResolver().invalidate();
        }
    }

    private Properties snapshotProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    private void restoreProperties(Properties previous) {
        properties.clear();
        properties.putAll(previous);
        applyRuntime();
    }

    public void applyRuntime() {
        if (context != null) {
            context.setAttribute("contactUrl", properties.getProperty("contact.url"));
            context.setAttribute("title", properties.getProperty("title", "TicketChess"));
        }
        Set<String> adminsset = new HashSet<String>();
        for (String admin : properties.getProperty("admins", "test@test.com").split(",")) {
            String trim = admin.trim();
            if (!trim.isEmpty()) {
                adminsset.add(trim);
            }
        }
        admins = Collections.unmodifiableSet(adminsset);

        // Page holds platform defaults; per-request org branding is applied in MustacheMessageBodyWriter.
        page.setContactUrl(platformProperty("contact.url"));
        page.setTitle(platformProperty("title", "TicketChess"));
        page.setMembershipsEnabled(true);
        page.setEventsEnabled(true);
        page.setPaymentsEnabled(true);
        page.setClubRegisterEnabled(true);
        page.setOrgName(firstNonBlank(platformProperty("org.name"), platformProperty("title"), "TicketChess"));
        page.setOrgEmail(blankToNull(platformProperty("org.email")));
        page.setOrgAddress(blankToNull(platformProperty("org.address")));
        page.setOrgHostingAddress(
                blankToNull(firstNonBlank(platformProperty("org.hosting.address"), platformProperty("org.address"))));
        page.setAccountUrl(blankToNull(getOauthAccountUrl()));
        page.setSourceUrl(OrgConfig.SOURCE_URL);
        page.setLogoUrl(logoFileExists() ? "/logo" : null);
        page.setBackgroundUrl(backgroundFileExists() ? "/background" : null);

        if (!properties.containsKey("jwt.key") && (isTestModeProperty() || isTestEnvironment())) {
            logger.warn(
                    "No JWT key provided in properties, using default. Allowed only in testmode or test environment.");
        } else if (!properties.containsKey("jwt.key")) {
            logger.warn(
                    "No JWT key provided in properties, using default. This is not secure and should be changed in production.");
        }
        String jwtKeyRaw = properties.getProperty("jwt.key", DEFAULT_JWT_KEY);
        byte[] jwtKeyBytes = jwtKeyRaw.getBytes(StandardCharsets.UTF_8);
        if (jwtKeyBytes.length < JWT_KEY_MIN_BYTES) {
            throw new IllegalStateException(
                    "jwt.key must be at least " + JWT_KEY_MIN_BYTES + " UTF-8 bytes for " + JWT_ALGORITHM.getId());
        }
        keys = Keys.hmacShaKeyFor(jwtKeyBytes);

        testMode = isTestModeProperty();
        pendingQueueOffset = Integer.parseInt(properties.getProperty("pendingQueueOffset", "0"));
    }

    public void applyOrg(AbstractMail mail) {
        if (mail != null) {
            // Use request-scoped org branding, not the platform page defaults (e.g. title=Event Test).
            mail.setOrgName(pageForRequest().getOrgName());
        }
    }

    public Map<String, String> getOrgFormValues() {
        Map<String, String> map = new LinkedHashMap<>();
        Organisation org = OrgScope.currentOrg();
        if (org != null) {
            map.putAll(new OrgConfig(org).toMap(false));
        }
        for (String key : properties.stringPropertyNames()) {
            if (SECRET_KEYS.contains(key)) {
                continue;
            }
            if (org != null && OrgConfig.isOrgScoped(key)) {
                map.putIfAbsent(key, "");
                continue;
            }
            map.putIfAbsent(key, nullToEmpty(platformProperty(key)));
        }
        putIfBlank(map, "oauth.clientId", getOauthClientId());
        putIfBlank(map, "oauth.authorizationUrl", getOauthAuthorizationUrl());
        putIfBlank(map, "oauth.tokenUrl", getOauthTokenUrl());
        putIfBlank(map, "oauth.scope", getOauthScope());
        putIfBlank(map, "oauth.logoutUrl", getOauthLogoutUrl());
        putIfBlank(map, "oauth.accountUrl", getOauthAccountUrl());
        putIfBlank(map, "oauth.userinfoUrl", getOauthUserinfoUrl());
        putIfBlank(map, "invoice.seller.name", invoiceProperty("invoice.seller.name", "org.name"));
        putIfBlank(map, "invoice.seller.address1", invoiceProperty("invoice.seller.address1", "org.address"));
        putIfBlank(map, "invoice.seller.email", invoiceProperty("invoice.seller.email", "org.email"));
        putIfBlank(map, "invoice.seller.website", invoiceProperty("invoice.seller.website", "contact.url"));
        return map;
    }

    /** Raw value from {@code params.properties}, ignoring the current organisation overlay. */
    public String platformProperty(String key) {
        if (properties instanceof OrgProperties orgProperties) {
            return orgProperties.rawProperty(key);
        }
        return properties.getProperty(key);
    }

    public String platformProperty(String key, String defaultValue) {
        String value = platformProperty(key);
        return value == null ? defaultValue : value;
    }

    private static void putIfBlank(Map<String, String> map, String key, String value) {
        if (blankToNull(map.get(key)) == null && blankToNull(value) != null) {
            map.put(key, value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String invoiceProperty(String invoiceKey, String orgKey) {
        return configured(properties, invoiceKey, orgKey);
    }

    public static String configured(Properties properties, String key, String fallbackKey) {
        String value = blankToNull(properties == null ? null : properties.getProperty(key));
        if (value != null) {
            return value;
        }
        if (fallbackKey != null && properties != null) {
            value = blankToNull(properties.getProperty(fallbackKey));
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    public boolean isOauthEnabled() {
        return blankToNull(getOauthClientId()) != null;
    }

    public String getOauthClientId() {
        return firstNonBlank(getProperty("oauth.clientId"), getProperty("keycloak.CLIENT_ID"));
    }

    public String getOauthClientSecret() {
        return firstNonBlank(getProperty("oauth.clientSecret"), getProperty("keycloak.CLIENT_SECRET"));
    }

    public String getOauthAuthorizationUrl() {
        return firstNonBlank(getProperty("oauth.authorizationUrl"), getProperty("keycloak.url"));
    }

    public String getOauthTokenUrl() {
        String explicit = blankToNull(getProperty("oauth.tokenUrl"));
        if (explicit != null) {
            return explicit;
        }
        return replaceAuthSuffix(getOauthAuthorizationUrl(), "/token");
    }

    public String getOauthLogoutUrl() {
        String explicit = blankToNull(getProperty("oauth.logoutUrl"));
        if (explicit != null) {
            return explicit;
        }
        // Only Keycloak/OIDC ".../protocol/openid-connect/auth" (not Google ".../v2/auth").
        String auth = getOauthAuthorizationUrl();
        if (auth != null && auth.contains("/protocol/openid-connect/auth")) {
            return replaceAuthSuffix(auth, "/logout");
        }
        return null;
    }

    public String getOauthAccountUrl() {
        String explicit = blankToNull(getProperty("oauth.accountUrl"));
        if (explicit != null) {
            return explicit;
        }
        String auth = getOauthAuthorizationUrl();
        if (auth != null && auth.contains("/protocol/openid-connect/auth")) {
            return auth.substring(0, auth.indexOf("/protocol/openid-connect/auth")) + "/account";
        }
        return null;
    }

    public String getOauthUserinfoUrl() {
        return blankToNull(getProperty("oauth.userinfoUrl"));
    }

    public String getOauthScope() {
        return firstNonBlank(getProperty("oauth.scope"), "openid email profile");
    }

    public String getOauthCallbackPath() {
        if (blankToNull(getProperty("oauth.clientId")) != null) {
            return "/oauth-callback";
        }
        return "/keycloak-callback";
    }

    public String getOauthRedirectUri() {
        return getPublicBaseUrl() + getRequestContextPath() + getOauthCallbackPath();
    }

    /**
     * Public site origin for the current organisation when a request is in scope, otherwise the
     * platform {@code baseurl}. Used for login, OAuth redirect and logout return URLs.
     */
    public String getPublicBaseUrl() {
        Organisation org = OrgScope.currentOrg();
        if (org != null) {
            String orgBase = blankToNull(OrgConfig.normalizeBaseUrl(org.getBaseUrl()));
            if (orgBase != null) {
                return orgBase;
            }
        }
        return OrgConfig.normalizeBaseUrl(platformProperty("baseurl", "http://localhost:8080"));
    }

    public boolean isSecretConfigured(String key) {
        return blankToNull(getProperty(key)) != null
                || ("oauth.clientSecret".equals(key) && blankToNull(getOauthClientSecret()) != null);
    }

    private boolean logoFileExists() {
        Path dir = OrganisationFiles.currentDir(this);
        return Files.isRegularFile(dir.resolve("logo.png"))
                || Files.isRegularFile(dir.resolve("logo.jpg"))
                || Files.isRegularFile(dir.resolve("logo.jpeg"))
                || Files.isRegularFile(dir.resolve("logo.webp"));
    }

    private boolean backgroundFileExists() {
        Path dir = OrganisationFiles.currentDir(this);
        return Files.isRegularFile(dir.resolve("background.png"))
                || Files.isRegularFile(dir.resolve("background.jpg"))
                || Files.isRegularFile(dir.resolve("background.jpeg"))
                || Files.isRegularFile(dir.resolve("background.webp"));
    }

    private static String replaceAuthSuffix(String authUrl, String replacement) {
        if (authUrl != null && authUrl.endsWith("/auth")) {
            return authUrl.substring(0, authUrl.length() - "/auth".length()) + replacement;
        }
        return null;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Non-blank Stripe publishable key, or {@code null} when unset or empty. */
    public static String getStripePublicKey(Properties properties) {
        return blankToNull(properties.getProperty("stripe.public"));
    }

    /** Card payments are simulated when enabled and no publishable key is configured. */
    public static boolean isStripeSimulated(Properties properties) {
        if (getStripePublicKey(properties) != null) {
            return false;
        }
        return Boolean.parseBoolean(properties.getProperty("stripe.simuled",
                properties.getProperty("stripe.simulated", "false")));
    }

    public static boolean isStripeCardEnabledForEvents(Properties properties) {
        return OrgConfig.isFeatureEnabled(properties.getProperty(OrgConfig.FEATURE_EVENTS))
                && Boolean.parseBoolean(properties.getProperty("stripe.card.events", "true"));
    }

    public static boolean isStripeCardEnabledForMemberships(Properties properties) {
        return OrgConfig.isFeatureEnabled(properties.getProperty(OrgConfig.FEATURE_MEMBERSHIPS))
                && Boolean.parseBoolean(properties.getProperty("stripe.card.memberships", "true"));
    }

    public static boolean isBankTransferEnabledForEvents(Properties properties) {
        return OrgConfig.isFeatureEnabled(properties.getProperty(OrgConfig.FEATURE_EVENTS))
                && Boolean.parseBoolean(properties.getProperty("payment.transfer.events", "true"));
    }

    public static boolean isBankTransferEnabledForMemberships(Properties properties) {
        return OrgConfig.isFeatureEnabled(properties.getProperty(OrgConfig.FEATURE_MEMBERSHIPS))
                && Boolean.parseBoolean(properties.getProperty("payment.transfer.memberships", "true"));
    }

    public boolean isTestMode() {
        return testMode;
    }

    /** Dev-only login via {@code /auth-sim} when OAuth is not configured. */
    public boolean isAuthSimEnabled() {
        return testMode && !isOauthEnabled();
    }

    public void validateSecurity() {
        if (testMode || isTestEnvironment()) {
            return;
        }
        if (!isOauthEnabled()) {
            throw new IllegalStateException(
                    "Production requires OAuth (oauth.clientId). Set testmode=true for local development only.");
        }
        if (!hasSecureJwtKey()) {
            throw new IllegalStateException(
                    "Production requires a custom jwt.key (at least 32 UTF-8 bytes, not a placeholder).");
        }
    }

    static boolean isTestEnvironment() {
        return "true".equalsIgnoreCase(System.getProperty("test"))
                || "true".equalsIgnoreCase(System.getenv("TICKETCHESS_TEST"));
    }

    boolean hasSecureJwtKey() {
        String jwtKey = platformProperty("jwt.key");
        if (jwtKey == null || jwtKey.isBlank()) {
            return false;
        }
        if (DEFAULT_JWT_KEY.equals(jwtKey)) {
            return false;
        }
        if ("change-me-in-production-use-a-long-random-string".equals(jwtKey)) {
            return false;
        }
        if ("integration-test-jwt-key-not-for-production-use-only".equals(jwtKey)) {
            return false;
        }
        return jwtKey.getBytes(StandardCharsets.UTF_8).length >= JWT_KEY_MIN_BYTES;
    }

    private boolean isTestModeProperty() {
        return Boolean.parseBoolean(platformProperty("testmode", "false"));
    }

    public int getPendingQueueOffset() {
        return pendingQueueOffset;
    }

    public Properties getProperties() {
        return properties;
    }

    public Set<String> getAdmins() {
        return admins;
    }

    public SecretKey getKeys() {
        return keys;
    }

    /** Servlet context path used for login and OAuth return URLs. */
    public String getRequestContextPath() {
        RequestContext ctx = RequestContext.current();
        if (ctx != null) {
            return ctx.effectiveContextPath();
        }
        return context != null ? context.getContextPath() : "";
    }

    public String getLoginUrl() {
        String contextPath = getRequestContextPath();
        if (isOauthEnabled()) {
            String scope = URLEncoder.encode(getOauthScope(), StandardCharsets.UTF_8);
            return getOauthAuthorizationUrl() + "?client_id="
                    + URLEncoder.encode(getOauthClientId(), StandardCharsets.UTF_8)
                    + "&redirect_uri="
                    + URLEncoder.encode(getOauthRedirectUri(), StandardCharsets.UTF_8)
                    + "&response_type=code&scope="
                    + scope
                    + "&state=";
        }
        // Relative so auth-sim stays on the host:port the browser is already using
        // (org baseurl may omit a non-default port such as :8080).
        return contextPath + "/auth-sim?state=";
    }

    public Page getPage() {
        return page;
    }

    private boolean hasMembershipNotifEmails() {
        String raw = getProperty("membership.notif.emails");
        if (raw == null || raw.isBlank()) {
            raw = platformProperty("membership.notif.emails", "");
        }
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String value : raw.split(",")) {
            if (!value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Feature toggles default to enabled when unset; only explicit {@code false} disables. */
    public boolean isFeatureEnabled(String key) {
        return OrgConfig.isFeatureEnabled(getProperty(key));
    }

    /** Plugins enabled for the current organisation (derived from {@code feature.*} until a list is stored). */
    public Set<String> enabledPlugins() {
        return OrgConfig.resolveEnabledPlugins(this::getProperty);
    }

    /** Request-scoped page view: current org identity + per-org logo/background when available. */
    public Page pageForRequest() {
        Page base = page;
        Page view = new Page();
        view.setTitle(firstNonBlank(getProperty("title"), base.getTitle(), "TicketChess"));
        view.setContactUrl(firstNonBlank(getProperty("contact.url"), base.getContactUrl()));
        Set<String> plugins = enabledPlugins();
        boolean memberships = plugins.contains(PluginIds.MEMBERSHIP);
        boolean events = plugins.contains(PluginIds.EVENTS);
        boolean payments = plugins.contains(PluginIds.PAYMENTS);
        view.setMembershipsEnabled(memberships);
        view.setEventsEnabled(events);
        view.setPaymentsEnabled(payments);
        view.setClubRegisterEnabled(memberships);
        Organisation org = OrgScope.currentOrg();
        if (org != null && blankToNull(org.getName()) != null) {
            view.setOrgName(org.getName().trim());
        } else {
            view.setOrgName(firstNonBlank(getProperty("org.name"), getProperty("title"), base.getOrgName(), "TicketChess"));
        }
        view.setOrgEmail(blankToNull(firstNonBlank(getProperty("org.email"), base.getOrgEmail())));
        view.setOrgAddress(blankToNull(firstNonBlank(getProperty("org.address"), base.getOrgAddress())));
        view.setOrgHostingAddress(blankToNull(firstNonBlank(
                getProperty("org.hosting.address"),
                getProperty("org.address"),
                base.getOrgHostingAddress(),
                base.getOrgAddress())));
        view.setAccountUrl(blankToNull(getOauthAccountUrl()));
        view.setSourceUrl(OrgConfig.SOURCE_URL);
        view.setLogoUrl(logoFileExists() ? "/logo" : null);
        view.setBackgroundUrl(backgroundFileExists() ? "/background" : null);
        OrgUiStyle.Resolved ui = OrgUiStyle.resolve(this::getProperty);
        view.setPrimaryColor(ui.primaryColor());
        view.setSecondaryColor(ui.secondaryColor());
        view.setPrimaryRgb(ui.primaryRgb());
        view.setSecondaryRgb(ui.secondaryRgb());
        view.setPrimaryHover(ui.primaryHover());
        view.setSecondaryHover(ui.secondaryHover());
        view.setFontFamily(ui.fontFamily());
        view.setCustomFont(ui.fontFamily() != null && !ui.fontFamily().isBlank());
        view.setGoogleFontHref(ui.googleFontHref());
        return view;
    }
}
