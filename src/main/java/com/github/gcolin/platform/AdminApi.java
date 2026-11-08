package com.github.gcolin.platform;

import com.github.gcolin.auth.LoggedOnly;
import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.payment.RibService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("admin")
@LoggedOnly
public class AdminApi {

    private static final Logger logger = LoggerFactory.getLogger(AdminApi.class);

    /** Org settings edited under {@code /admin/organisations/{id}}. */
    private static final Set<String> MOVED_ORG_TABS = Set.of(
            "club",
            "features",
            "identity",
            "contact",
            "mail",
            "invoice",
            "stripe",
            "payment",
            "files",
            "appearance");

    private static String normalizeMovedOrgTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "features";
        }
        String t = tab.trim();
        if ("club".equals(t)) {
            return "features";
        }
        if ("invoice".equals(t) || "stripe".equals(t)) {
            return "payment";
        }
        return t;
    }

    /** Platform / SSO settings edited on {@code /admin/org}. */
    static final Map<String, Set<String>> TAB_KEYS = Map.of(
            "oauth",
            Set.of(
                    "oauth.clientId",
                    "oauth.clientSecret",
                    "oauth.authorizationUrl",
                    "oauth.tokenUrl",
                    "oauth.scope",
                    "oauth.logoutUrl",
                    "oauth.accountUrl",
                    "oauth.userinfoUrl"),
            "system",
            Set.of(
                    "baseurl",
                    "jwt.key",
                    "db.host",
                    "db.name",
                    "db.user",
                    "db.pass",
                    "db.type"));

    @Inject
    private LoggedUser user;

    @Inject
    private Caches caches;

    @Inject
    private RibService ribService;

    @Inject
    private LogoService logoService;

    @Inject
    private SignatureService signatureService;

    @Inject
    private BackgroundService backgroundService;

    @Inject
    private Config config;

    @Inject
    private SendMail sendMail;

    @Context
    private UriInfo uriInfo;

    @GET
    public Response page(@QueryParam("success") @DefaultValue("") String success) {
        if (!user.canSeeAdminMenu()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        Page page = config.pageForRequest();
        boolean eventsOn = page.isEventsEnabled();
        boolean membershipsOn = page.isMembershipsEnabled();
        boolean paymentsOn = page.isPaymentsEnabled();
        Map<String, Object> model = new HashMap<>();
        model.put("success", success);
        model.put(
                "adminSectionEvent",
                (eventsOn || paymentsOn)
                        && (user.canManageEvents() || user.isAdmin() || user.canManagePayments()));
        model.put("adminSectionMembership", membershipsOn && user.canManageMemberships());
        model.put("adminSectionUsers", user.canManageEvents() || user.isAdmin());
        model.put(
                "userCanViewStatistics",
                (eventsOn || paymentsOn) && (user.canManagePayments() || user.canManageEvents()));
        model.put("featureEvents", eventsOn);
        model.put("featureMemberships", membershipsOn);
        model.put("featurePayments", paymentsOn);
        Organisation currentOrg = OrgScope.currentOrg();
        if (currentOrg != null && currentOrg.getId() != null) {
            model.put("currentOrgId", currentOrg.getId());
            model.put("currentOrgName", currentOrg.getName());
        }
        return Response.ok(new MustacheHtml(model, "platform/admin.mustache"))
                .build();
    }

    @GET
    @Path("clear-cache")
    @RequireRole(RoleCode.ADMIN)
    public Response clearCache() {
        caches.getDebtCache().invalidateAll();
        caches.getRoleCache().invalidateAll();
        caches.getAllEvents().invalidateAll();
        caches.getEvent().invalidateAll();
        caches.getEventGroups().invalidateAll();

        URI redirect = Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                .path("admin")
                .queryParam("success", "cacheCleared")
                .build());
        return Response.seeOther(redirect).build();
    }

    @GET
    @Path("rib")
    @RequireRole(RoleCode.ADMIN)
    public Response ribRedirect() {
        URI requestUri = uriInfo.getRequestUri();
        String query = requestUri == null ? null : requestUri.getRawQuery();
        URI redirect = Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                .path("admin")
                .path("org")
                .replaceQuery(query)
                .build());
        return Response.seeOther(redirect).build();
    }

    @GET
    @Path("org")
    @RequireRole(RoleCode.ADMIN)
    public Response orgPage(
            @QueryParam("success") @DefaultValue("") String success,
            @QueryParam("error") @DefaultValue("") String error,
            @QueryParam("tab") @DefaultValue("oauth") String tab) {
        String effectiveTab = tab == null || tab.isBlank() ? "oauth" : tab.trim();
        if (MOVED_ORG_TABS.contains(effectiveTab)) {
            Organisation current = OrgScope.currentOrg();
            if (current != null && current.getId() != null) {
                String orgTab = normalizeMovedOrgTab(effectiveTab);
                var builder = uriInfo.getBaseUriBuilder()
                        .path("admin/organisations")
                        .path(String.valueOf(current.getId()))
                        .queryParam("tab", orgTab);
                if (success != null && !success.isBlank()) {
                    builder.queryParam("success", success);
                }
                if (error != null && !error.isBlank()) {
                    builder.queryParam("error", error);
                }
                return Response.seeOther(Redirects.toSameOriginRelative(builder.fragment(orgTab).build()))
                        .build();
            }
            effectiveTab = "oauth";
        }
        if (user == null || !user.isPlatformAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!TAB_KEYS.containsKey(effectiveTab)) {
            effectiveTab = "oauth";
        }
        Map<String, Object> model = new HashMap<>();
        model.put("success", success);
        model.put("error", error);
        model.put("tab", effectiveTab);
        model.put("showSystemTab", true);
        model.put("titleKey", "page.adminOrg");
        model.put("publicBaseUrl", config.getPublicBaseUrl());
        Organisation currentOrg = OrgScope.currentOrg();
        if (currentOrg != null) {
            model.put("currentOrgName", currentOrg.getName());
            model.put("currentOrgId", currentOrg.getId());
        }
        Map<String, String> cfg = new LinkedHashMap<>(config.getOrgFormValues());
        for (Set<String> keys : TAB_KEYS.values()) {
            for (String key : keys) {
                cfg.putIfAbsent(key, "");
            }
        }
        for (String key : TAB_KEYS.get("system")) {
            if ("baseurl".equals(key)) {
                cfg.put(key, config.platformProperty("baseurl", ""));
            } else {
                String platformValue = config.platformProperty(key);
                cfg.put(key, platformValue == null ? "" : platformValue);
            }
        }
        model.put("cfg", cfg);
        Map<String, Boolean> secrets = new LinkedHashMap<>();
        for (String key : Config.SECRET_KEYS) {
            secrets.put(key, config.isSecretConfigured(key));
        }
        model.put("secretsConfigured", secrets);
        MustacheOrgConfigView.enrich(model);
        return Response.ok(new MustacheHtml(model, "platform/adminOrg.mustache")).build();
    }

    @POST
    @Path("org/config")
    @RequireRole(RoleCode.ADMIN)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response saveConfig(MultivaluedMap<String, String> form) {
        if (user == null || !user.isPlatformAdmin()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        String tab = formValue(form, "tab");
        if (tab == null || !TAB_KEYS.containsKey(tab)) {
            return redirectToOrg("error", "configInvalid", "oauth");
        }
        Map<String, String> updates = new LinkedHashMap<>();
        for (String key : TAB_KEYS.get(tab)) {
            String value = formValue(form, key);
            updates.put(key, value == null ? "" : value.trim());
        }
        try {
            config.updateProperties(updates);
            if ("system".equals(tab) && sendMail != null) {
                sendMail.reloadCredentials();
            }
            return redirectToOrg("success", "configSaved", tab);
        } catch (IOException e) {
            logger.error("cannot save organisation config", e);
            return redirectToOrg("error", "configSaveFailed", tab);
        }
    }

    @POST
    @Path("org/rib")
    @RequireRole(RoleCode.ADMIN)
    @Consumes({"application/pdf", "application/x-pdf", "application/octet-stream", "*/*"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadRib(InputStream file) {
        try {
            ribService.save(file);
            return jsonRedirectToOrgFiles("success", "ribUploaded");
        } catch (WebApplicationException e) {
            String error = "ribUploadFailed";
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("PDF")) {
                error = "invalidRib";
            } else if (message.contains("too large")) {
                error = "ribTooLarge";
            }
            return jsonRedirectToOrgFiles("error", error);
        } catch (IOException e) {
            logger.error("cannot save RIB file", e);
            return jsonRedirectToOrgFiles("error", "ribUploadFailed");
        }
    }

    @DELETE
    @Path("org/rib")
    @RequireRole(RoleCode.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRib() {
        try {
            ribService.delete();
            Map<String, String> updates = new LinkedHashMap<>();
            updates.put("payment.transfer.events", "false");
            updates.put("payment.transfer.memberships", "false");
            config.updateProperties(updates);
            return jsonRedirectToOrgFiles("success", "ribDeleted");
        } catch (IOException e) {
            logger.error("cannot delete RIB file", e);
            return jsonRedirectToOrgFiles("error", "ribDeleteFailed");
        }
    }

    @POST
    @Path("org/logo")
    @RequireRole(RoleCode.ADMIN)
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadLogo(InputStream file) {
        try {
            logoService.save(file);
            config.applyRuntime();
            return jsonRedirectToOrgFiles("success", "logoUploaded");
        } catch (WebApplicationException e) {
            String error = "logoUploadFailed";
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("PNG") || message.contains("JPEG") || message.contains("WebP")) {
                error = "invalidLogo";
            } else if (message.contains("too large")) {
                error = "logoTooLarge";
            }
            return jsonRedirectToOrgFiles("error", error);
        } catch (IOException e) {
            logger.error("cannot save logo file", e);
            return jsonRedirectToOrgFiles("error", "logoUploadFailed");
        }
    }

    @DELETE
    @Path("org/logo")
    @RequireRole(RoleCode.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLogo() {
        try {
            logoService.delete();
            config.applyRuntime();
            return jsonRedirectToOrgFiles("success", "logoDeleted");
        } catch (IOException e) {
            logger.error("cannot delete logo file", e);
            return jsonRedirectToOrgFiles("error", "logoDeleteFailed");
        }
    }

    @POST
    @Path("org/signature")
    @RequireRole(RoleCode.ADMIN)
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadSignature(InputStream file) {
        try {
            signatureService.save(file);
            return jsonRedirectToOrgFiles("success", "signatureUploaded");
        } catch (WebApplicationException e) {
            String error = "signatureUploadFailed";
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("PNG") || message.contains("JPEG") || message.contains("WebP")) {
                error = "invalidSignature";
            } else if (message.contains("too large")) {
                error = "signatureTooLarge";
            }
            return jsonRedirectToOrgFiles("error", error);
        } catch (IOException e) {
            logger.error("cannot save signature file", e);
            return jsonRedirectToOrgFiles("error", "signatureUploadFailed");
        }
    }

    @DELETE
    @Path("org/signature")
    @RequireRole(RoleCode.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSignature() {
        try {
            signatureService.delete();
            return jsonRedirectToOrgFiles("success", "signatureDeleted");
        } catch (IOException e) {
            logger.error("cannot delete signature file", e);
            return jsonRedirectToOrgFiles("error", "signatureDeleteFailed");
        }
    }

    @POST
    @Path("org/background")
    @RequireRole(RoleCode.ADMIN)
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBackground(InputStream file) {
        try {
            backgroundService.save(file);
            config.applyRuntime();
            return jsonRedirectToOrgFiles("success", "backgroundUploaded");
        } catch (WebApplicationException e) {
            String error = "backgroundUploadFailed";
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("PNG") || message.contains("JPEG") || message.contains("WebP")) {
                error = "invalidBackground";
            } else if (message.contains("too large")) {
                error = "backgroundTooLarge";
            }
            return jsonRedirectToOrgFiles("error", error);
        } catch (IOException e) {
            logger.error("cannot save background file", e);
            return jsonRedirectToOrgFiles("error", "backgroundUploadFailed");
        }
    }

    @DELETE
    @Path("org/background")
    @RequireRole(RoleCode.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackground() {
        try {
            backgroundService.delete();
            config.applyRuntime();
            return jsonRedirectToOrgFiles("success", "backgroundDeleted");
        } catch (IOException e) {
            logger.error("cannot delete background file", e);
            return jsonRedirectToOrgFiles("error", "backgroundDeleteFailed");
        }
    }

    private Response redirectToOrg(String queryName, String queryValue, String tab) {
        return Response.seeOther(orgRedirectUri(queryName, queryValue, tab)).build();
    }

    private Response jsonRedirectToOrgFiles(String queryName, String queryValue) {
        String json = new JSONObject()
                .put("redirect", orgFilesRedirectUri(queryName, queryValue).toString())
                .toString();
        return Response.ok(json).build();
    }

    private URI orgFilesRedirectUri(String queryName, String queryValue) {
        Organisation current = OrgScope.currentOrg();
        if (current != null && current.getId() != null) {
            return Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                    .path("admin/organisations")
                    .path(String.valueOf(current.getId()))
                    .queryParam(queryName, queryValue)
                    .queryParam("tab", "files")
                    .fragment("files")
                    .build());
        }
        return orgRedirectUri(queryName, queryValue, "oauth");
    }

    private URI orgRedirectUri(String queryName, String queryValue, String tab) {
        return Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                .path("admin")
                .path("org")
                .queryParam(queryName, queryValue)
                .queryParam("tab", tab)
                .fragment(tab)
                .build());
    }

    private static String formValue(MultivaluedMap<String, String> form, String key) {
        return form == null ? null : form.getFirst(key);
    }
}
