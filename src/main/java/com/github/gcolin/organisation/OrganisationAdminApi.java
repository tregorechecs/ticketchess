package com.github.gcolin.organisation;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.payment.RibService;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.BackgroundService;
import com.github.gcolin.platform.ClientDisconnect;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.LogoService;
import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.platform.MustacheOrgConfigView;
import com.github.gcolin.platform.Redirects;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.platform.ServiceUtils;
import com.github.gcolin.platform.SignatureService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequireRole(RoleCode.ADMIN)
@Path("admin/organisations")
public class OrganisationAdminApi {

    private static final Logger logger = LoggerFactory.getLogger(OrganisationAdminApi.class);
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** Organisation settings tabs (moved from {@code AdminApi} club/invoice/stripe). */
    static final Map<String, Set<String>> SETTINGS_TAB_KEYS = Map.of(
            "features",
            Set.of(
                    OrgConfig.FEATURE_MEMBERSHIPS,
                    OrgConfig.FEATURE_EVENTS,
                    OrgConfig.FEATURE_PAYMENTS),
            "identity",
            Set.of("title", "org.name"),
            "contact",
            Set.of(
                    "contact.url",
                    "org.email",
                    "org.address"),
            "mail",
            Set.of("mail.USER_NAME", "mail.PASSWORD", "membership.notif.emails"),
            "payment",
            Set.of(
                    "invoice.seller.address2",
                    "invoice.seller.zip",
                    "invoice.seller.city",
                    "invoice.seller.country",
                    "invoice.seller.phone",
                    "invoice.seller.siret",
                    "invoice.seller.rna",
                    "invoice.seller.prefecture",
                    "invoice.number.prefix",
                    "invoice.footer",
                    "invoice.vat.notice",
                    "invoice.donation.object",
                    "invoice.donation.signatory",
                    "invoice.donation.signatoryTitle",
                    "invoice.donation.prefix",
                    "stripe.public",
                    "stripe.secret",
                    "stripe.keyprefix",
                    "stripe.simuled",
                    "stripe.card.events",
                    "stripe.card.memberships",
                    "payment.transfer.events",
                    "payment.transfer.memberships"),
            "appearance",
            Set.of(OrgUiStyle.KEY_PRIMARY, OrgUiStyle.KEY_SECONDARY, OrgUiStyle.KEY_FONT));

    static final Set<String> BOOLEAN_KEYS = Set.of(
            "stripe.simuled",
            "stripe.card.events",
            "stripe.card.memberships",
            "payment.transfer.events",
            "payment.transfer.memberships",
            OrgConfig.FEATURE_MEMBERSHIPS,
            OrgConfig.FEATURE_EVENTS,
            OrgConfig.FEATURE_PAYMENTS);

    private static final Set<String> SETTINGS_TABS = Set.of(
            "features", "identity", "contact", "mail", "payment", "appearance", "files");

    /** Legacy query-tab names kept for redirects / bookmarks. */
    private static String normalizeSettingsTab(String tab) {
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

    private static final Set<String> PLATFORM_SETTINGS_TABS = Set.of("oauth", "system");

    @Inject
    private OrganisationDao organisationDao;

    @Inject
    private LoggedUser user;

    @Inject
    private Config config;

    @Inject
    private LogoService logoService;

    @Inject
    private SignatureService signatureService;

    @Inject
    private BackgroundService backgroundService;

    @Inject
    private RibService ribService;

    @Inject
    private SendMail sendMail;

    @Context
    UriInfo uriInfo;

    @GET
    public Response list(@QueryParam("success") String success) {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        Map<String, Object> model = new HashMap<>();
        model.put("organisations", organisationDao.allOrdered());
        model.put("success", success == null ? "" : success);
        model.put("titleKey", "page.organisations");
        model.put("activePageTitleKey", "breadcrumb.organisations");
        return Response.ok(new MustacheHtml(model, "organisation/organisationAdmin.mustache")).build();
    }

    @GET
    @Path("new")
    public Response createPage() {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        return Response.ok(form(new Organisation(), null, "identity", "", "")).build();
    }

    @GET
    @Path("{id}")
    public Response editPage(
            @PathParam("id") int id,
            @QueryParam("tab") @DefaultValue("") String tab,
            @QueryParam("success") @DefaultValue("") String success,
            @QueryParam("error") @DefaultValue("") String error) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        String requestedTab = tab == null ? "" : tab.trim();
        if (PLATFORM_SETTINGS_TABS.contains(requestedTab)) {
            var builder = uriInfo.getBaseUriBuilder().path("admin/org").queryParam("tab", requestedTab);
            if (success != null && !success.isBlank()) {
                builder.queryParam("success", success);
            }
            if (error != null && !error.isBlank()) {
                builder.queryParam("error", error);
            }
            return Response.seeOther(Redirects.toSameOriginRelative(builder.fragment(requestedTab).build()))
                    .build();
        }
        return Response.ok(form(org, null, tab, success, error)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(
            @FormParam("base_url") String baseUrl,
            @FormParam("name") String name,
            @FormParam("super_user_email") String superUserEmail) {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        Organisation org = new Organisation();
        applyIdentity(org, baseUrl, name, superUserEmail);

        String error = validate(org);
        if (error != null) {
            return Response.ok(form(org, error, "identity", "", "")).build();
        }

        organisationDao.persist(org);
        AppContext.get().organisationResolver().invalidate();
        logger.info("Created organisation {} ({})", org.getName(), org.getBaseUrl());
        return Response.seeOther(Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                        .path("admin/organisations")
                        .queryParam("success", "created")
                        .build()))
                .build();
    }

    @POST
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response update(
            @PathParam("id") int id,
            @FormParam("base_url") String baseUrl,
            @FormParam("name") String name,
            @FormParam("super_user_email") String superUserEmail) {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        applyIdentity(org, baseUrl, name, superUserEmail);

        String error = validate(org);
        if (error != null) {
            return Response.ok(form(org, error, "identity", "", "")).build();
        }

        organisationDao.merge(org);
        AppContext.get().organisationResolver().invalidate();
        logger.info("Updated organisation {} ({})", org.getName(), org.getBaseUrl());
        return Response.seeOther(Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                        .path("admin/organisations")
                        .path(String.valueOf(org.getId()))
                        .queryParam("success", "updated")
                        .queryParam("tab", "identity")
                        .build()))
                .build();
    }

    @POST
    @Path("{id}/config")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response saveConfig(@PathParam("id") int id, MultivaluedMap<String, String> form) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        String tab = normalizeSettingsTab(formValue(form, "tab"));
        if (!SETTINGS_TAB_KEYS.containsKey(tab)) {
            return redirectToEdit(org, "error", "configInvalid", "features");
        }
        if ("payment".equals(tab) && !new OrgConfig(org).isFeatureEnabledKey(OrgConfig.FEATURE_PAYMENTS)) {
            return redirectToEdit(org, "error", "configInvalid", "features");
        }
        if ("identity".equals(tab) && user != null && user.isPlatformAdmin()) {
            String name = formValue(form, "org.name");
            applyIdentity(
                    org,
                    formValue(form, "base_url"),
                    name != null && !name.isBlank() ? name : org.getName(),
                    formValue(form, "super_user_email"));
            String identityError = validate(org);
            if (identityError != null) {
                return Response.ok(form(org, identityError, "identity", "", "")).build();
            }
            organisationDao.merge(org);
            AppContext.get().organisationResolver().invalidate();
        }
        Map<String, String> updates = new LinkedHashMap<>();
        for (String key : SETTINGS_TAB_KEYS.get(tab)) {
            String value = formValue(form, key);
            if (BOOLEAN_KEYS.contains(key)) {
                updates.put(
                        key,
                        value != null && !value.isBlank() && !"false".equalsIgnoreCase(value) ? "true" : "false");
            } else {
                updates.put(key, value == null ? "" : value.trim());
            }
        }
        if ("appearance".equals(tab)) {
            updates.put(
                    OrgUiStyle.KEY_PRIMARY,
                    OrgUiStyle.sanitizeColor(updates.get(OrgUiStyle.KEY_PRIMARY), OrgUiStyle.DEFAULT_PRIMARY));
            updates.put(
                    OrgUiStyle.KEY_SECONDARY,
                    OrgUiStyle.sanitizeColor(updates.get(OrgUiStyle.KEY_SECONDARY), OrgUiStyle.DEFAULT_SECONDARY));
            updates.put(OrgUiStyle.KEY_FONT, OrgUiStyle.sanitizeFontId(updates.get(OrgUiStyle.KEY_FONT)));
        }
        if ("features".equals(tab)) {
            if (!"true".equals(updates.get(OrgConfig.FEATURE_EVENTS))) {
                updates.put("stripe.card.events", "false");
                updates.put("payment.transfer.events", "false");
            }
            if (!"true".equals(updates.get(OrgConfig.FEATURE_MEMBERSHIPS))) {
                updates.put("stripe.card.memberships", "false");
                updates.put("payment.transfer.memberships", "false");
            }
        }
        if ("mail".equals(tab) && !new OrgConfig(org).isFeatureEnabledKey(OrgConfig.FEATURE_MEMBERSHIPS)) {
            // Field is hidden when memberships are off — keep the stored value.
            updates.remove("membership.notif.emails");
        }
        if ("payment".equals(tab)) {
            OrgConfig orgConfig = new OrgConfig(org);
            boolean eventsOn = orgConfig.isFeatureEnabledKey(OrgConfig.FEATURE_EVENTS);
            boolean membershipsOn = orgConfig.isFeatureEnabledKey(OrgConfig.FEATURE_MEMBERSHIPS);
            boolean simuled = "true".equals(updates.get("stripe.simuled"));
            if (simuled) {
                // Disabled key fields are omitted from the POST — keep existing values.
                updates.remove("stripe.public");
                updates.remove("stripe.secret");
                updates.remove("stripe.keyprefix");
            } else {
                String publicKey = updates.get("stripe.public");
                if (publicKey == null || publicKey.isBlank()) {
                    updates.put("stripe.card.events", "false");
                    updates.put("stripe.card.memberships", "false");
                }
            }
            if (!eventsOn) {
                updates.put("stripe.card.events", "false");
                updates.put("payment.transfer.events", "false");
            }
            if (!membershipsOn) {
                updates.put("stripe.card.memberships", "false");
                updates.put("payment.transfer.memberships", "false");
            }
        }
        try {
            withOrgScope(org, () -> {
                if ("payment".equals(tab) && !ribService.exists()) {
                    updates.put("payment.transfer.events", "false");
                    updates.put("payment.transfer.memberships", "false");
                }
                config.updateProperties(updates);
                if ("mail".equals(tab) && sendMail != null) {
                    sendMail.reloadCredentials();
                }
            });
            return redirectToEdit(org, "success", "configSaved", tab);
        } catch (IOException e) {
            logger.error("cannot save organisation config for org {}", org.getId(), e);
            return redirectToEdit(org, "error", "configSaveFailed", tab);
        }
    }

    @POST
    @Path("{id}/logo")
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadLogo(@PathParam("id") int id, InputStream file) {
        return uploadOrgFile(id, file, logoService::save, () -> config.applyRuntime(), "logo", "invalidLogo", "logoTooLarge",
                "logoUploadFailed", "logoUploaded");
    }

    @DELETE
    @Path("{id}/logo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLogo(@PathParam("id") int id) {
        return deleteOrgFile(id, logoService::delete, () -> config.applyRuntime(), "logoDeleted", "logoDeleteFailed");
    }

    @POST
    @Path("{id}/signature")
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadSignature(@PathParam("id") int id, InputStream file) {
        return uploadOrgFile(
                id,
                file,
                signatureService::save,
                null,
                "signature",
                "invalidSignature",
                "signatureTooLarge",
                "signatureUploadFailed",
                "signatureUploaded");
    }

    @DELETE
    @Path("{id}/signature")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSignature(@PathParam("id") int id) {
        return deleteOrgFile(id, signatureService::delete, null, "signatureDeleted", "signatureDeleteFailed");
    }

    @POST
    @Path("{id}/background")
    @Consumes({"image/png", "image/jpeg", "image/webp", "application/octet-stream"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadBackground(@PathParam("id") int id, InputStream file) {
        return uploadOrgFile(
                id,
                file,
                backgroundService::save,
                () -> config.applyRuntime(),
                "background",
                "invalidBackground",
                "backgroundTooLarge",
                "backgroundUploadFailed",
                "backgroundUploaded");
    }

    @DELETE
    @Path("{id}/background")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteBackground(@PathParam("id") int id) {
        return deleteOrgFile(
                id, backgroundService::delete, () -> config.applyRuntime(), "backgroundDeleted", "backgroundDeleteFailed");
    }

    @POST
    @Path("{id}/rib")
    @Consumes({"application/pdf", "application/x-pdf", "application/octet-stream", "*/*"})
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadRib(@PathParam("id") int id, InputStream file) {
        return uploadOrgFile(id, file, ribService::save, null, "rib", "invalidRib", "ribTooLarge", "ribUploadFailed",
                "ribUploaded");
    }

    @DELETE
    @Path("{id}/rib")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRib(@PathParam("id") int id) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        try {
            withOrgScope(org, () -> {
                ribService.delete();
                Map<String, String> updates = new LinkedHashMap<>();
                updates.put("payment.transfer.events", "false");
                updates.put("payment.transfer.memberships", "false");
                config.updateProperties(updates);
            });
            return jsonRedirectToEdit(org, "success", "ribDeleted", "files");
        } catch (IOException e) {
            logger.error("cannot delete RIB for org {}", id, e);
            return jsonRedirectToEdit(org, "error", "ribDeleteFailed", "files");
        }
    }

    @GET
    @Path("{id}/files/logo")
    public Response downloadLogo(@PathParam("id") int id) {
        return streamOrgFile(id, () -> {
            if (!logoService.exists()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            StreamingOutput stream = ClientDisconnect.copyFile(logoService.getLogoFile());
            return Response.ok(stream)
                    .type(logoService.getContentType())
                    .header("Cache-Control", "no-cache")
                    .build();
        });
    }

    @GET
    @Path("{id}/files/signature")
    public Response downloadSignature(@PathParam("id") int id) {
        return streamOrgFile(id, () -> {
            if (!signatureService.exists()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            StreamingOutput stream = ClientDisconnect.copyFile(signatureService.getSignatureFile());
            return Response.ok(stream)
                    .type(signatureService.getContentType())
                    .header("Cache-Control", "no-cache")
                    .build();
        });
    }

    @GET
    @Path("{id}/files/background")
    public Response downloadBackground(@PathParam("id") int id) {
        return streamOrgFile(id, () -> {
            if (!backgroundService.exists()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            StreamingOutput stream = ClientDisconnect.copyFile(backgroundService.getBackgroundFile());
            return Response.ok(stream)
                    .type(backgroundService.getContentType())
                    .header("Cache-Control", "no-cache")
                    .build();
        });
    }

    @GET
    @Path("{id}/files/rib")
    public Response downloadRib(@PathParam("id") int id) {
        return streamOrgFile(id, () -> {
            if (!ribService.exists()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            StreamingOutput stream = ClientDisconnect.copyFile(ribService.getRibFile());
            return Response.ok(stream)
                    .type("application/pdf")
                    .header("Content-Disposition", "inline; filename=\"rib.pdf\"")
                    .build();
        });
    }

    private Response uploadOrgFile(
            int id,
            InputStream file,
            IoConsumer<InputStream> saver,
            Runnable afterSave,
            String label,
            String invalidError,
            String tooLargeError,
            String failedError,
            String successValue) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        try {
            withOrgScope(org, () -> {
                saver.accept(file);
                if (afterSave != null) {
                    afterSave.run();
                }
            });
            return jsonRedirectToEdit(org, "success", successValue, "files");
        } catch (WebApplicationException e) {
            String error = failedError;
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("PDF")
                    || message.contains("PNG")
                    || message.contains("JPEG")
                    || message.contains("WebP")) {
                error = invalidError;
            } else if (message.contains("too large")) {
                error = tooLargeError;
            }
            return jsonRedirectToEdit(org, "error", error, "files");
        } catch (IOException e) {
            logger.error("cannot save {} for org {}", label, id, e);
            return jsonRedirectToEdit(org, "error", failedError, "files");
        }
    }

    private Response deleteOrgFile(
            int id, IoRunnable deleter, Runnable afterDelete, String successValue, String failedError) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        try {
            withOrgScope(org, () -> {
                deleter.run();
                if (afterDelete != null) {
                    afterDelete.run();
                }
            });
            return jsonRedirectToEdit(org, "success", successValue, "files");
        } catch (IOException e) {
            logger.error("cannot delete file for org {}", id, e);
            return jsonRedirectToEdit(org, "error", failedError, "files");
        }
    }

    private Response streamOrgFile(int id, ResponseSupplier supplier) {
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Response denied = requireOrgAdminOrPlatform(org);
        if (denied != null) {
            return denied;
        }
        return withOrgScope(org, supplier);
    }

    private static void applyIdentity(Organisation org, String baseUrl, String name, String superUserEmail) {
        org.setBaseUrl(OrgConfig.normalizeBaseUrl(baseUrl));
        org.setName(name == null ? null : name.trim());
        org.setSuperUserEmail(superUserEmail == null ? null : superUserEmail.trim().toLowerCase(Locale.ROOT));
    }

    private MustacheHtml form(
            Organisation organisation, String identityError, String tab, String success, String queryError) {
        Map<String, Object> model = new HashMap<>();
        boolean editMode = organisation != null && organisation.getId() != null;
        boolean canEditIdentity = user != null && user.isPlatformAdmin();
        model.put("organisation", organisation != null ? organisation : new Organisation());
        model.put("editMode", editMode);
        model.put("canEditIdentity", canEditIdentity);
        model.put("titleKey", editMode ? "page.organisationEdit" : "page.organisationNew");
        model.put("activePageTitleKey", editMode ? "page.organisationEdit" : "page.organisationNew");

        String effectiveError = identityError != null && !identityError.isBlank()
                ? identityError
                : (queryError == null || queryError.isBlank() ? null : queryError);
        model.put("error", effectiveError);
        model.put("success", success == null ? "" : success);
        model.put("identityError", identityError != null && !identityError.isBlank() ? identityError : null);

        if (editMode) {
            enrichSettings(model, organisation, tab);
        } else {
            model.put("tab", "identity");
            model.put("tabIsidentity", true);
        }
        return new MustacheHtml(model, "organisation/organisationEdit.mustache");
    }

    private void enrichSettings(Map<String, Object> model, Organisation organisation, String tab) {
        OrgConfig orgConfig = new OrgConfig(organisation);
        boolean paymentsEnabled = orgConfig.isFeatureEnabledKey(OrgConfig.FEATURE_PAYMENTS);
        String effectiveTab = normalizeSettingsTab(tab);
        if (!SETTINGS_TABS.contains(effectiveTab)) {
            effectiveTab = "features";
        }
        if (!paymentsEnabled && "payment".equals(effectiveTab)) {
            effectiveTab = "features";
        }
        model.put("tab", effectiveTab);
        model.put(
                "settingsConfigAction",
                "admin/organisations/" + organisation.getId() + "/config");
        model.put("orgFilesBase", "admin/organisations/" + organisation.getId());

        try {
            withOrgScope(organisation, () -> {
                boolean ribAvailable = ribService.exists();
                model.put("ribAvailable", ribAvailable);
                model.put("logoAvailable", logoService.exists());
                model.put("signatureAvailable", signatureService.exists());
                model.put("backgroundAvailable", backgroundService.exists());
                model.put("publicBaseUrl", config.getPublicBaseUrl());
                model.put("clubRegisterEnabled", config.pageForRequest().isClubRegisterEnabled());

                Map<String, String> cfg = new LinkedHashMap<>(orgConfig.toMap(false));
                for (Set<String> keys : SETTINGS_TAB_KEYS.values()) {
                    for (String key : keys) {
                        cfg.putIfAbsent(key, "");
                    }
                }
                // Invoice display fallbacks (same idea as Config.getOrgFormValues).
                putIfBlank(
                        cfg,
                        "invoice.seller.name",
                        firstNonBlank(cfg.get("invoice.seller.name"), organisation.getName()));
                putIfBlank(
                        cfg,
                        "invoice.seller.address1",
                        firstNonBlank(cfg.get("invoice.seller.address1"), cfg.get("org.address")));
                putIfBlank(
                        cfg,
                        "invoice.seller.email",
                        firstNonBlank(cfg.get("invoice.seller.email"), cfg.get("org.email")));
                putIfBlank(
                        cfg,
                        "invoice.seller.website",
                        firstNonBlank(cfg.get("invoice.seller.website"), cfg.get("contact.url")));
                putIfBlank(cfg, OrgUiStyle.KEY_PRIMARY, OrgUiStyle.DEFAULT_PRIMARY);
                putIfBlank(cfg, OrgUiStyle.KEY_SECONDARY, OrgUiStyle.DEFAULT_SECONDARY);
                String selectedFont = OrgUiStyle.sanitizeFontId(cfg.get(OrgUiStyle.KEY_FONT));
                cfg.put(OrgUiStyle.KEY_FONT, selectedFont);
                List<Map<String, Object>> fontOptions = new java.util.ArrayList<>();
                for (OrgUiStyle.FontOption font : OrgUiStyle.fonts()) {
                    Map<String, Object> option = new HashMap<>();
                    option.put("id", font.id());
                    option.put("label", font.label());
                    option.put("selected", font.id().equals(selectedFont));
                    fontOptions.add(option);
                }
                model.put("fontOptions", fontOptions);
                model.put("cfg", cfg);

                Map<String, Boolean> secrets = new LinkedHashMap<>();
                for (String key : Config.SECRET_KEYS) {
                    if (OrgConfig.isOrgScoped(key) || OrgConfig.isSecret(key)) {
                        secrets.put(key, blankToNull(orgConfig.getProperty(key)) != null);
                    } else {
                        secrets.put(key, config.isSecretConfigured(key));
                    }
                }
                secrets.put("stripe.secret", blankToNull(orgConfig.getProperty("stripe.secret")) != null);
                secrets.put("mail.PASSWORD", blankToNull(orgConfig.getProperty("mail.PASSWORD")) != null);
                model.put("secretsConfigured", secrets);

                if (ribAvailable) {
                    try {
                        model.put("ribSize", ServiceUtils.readable(Files.size(ribService.getRibFile())));
                    } catch (IOException e) {
                        logger.warn("cannot read RIB file size for org {}", organisation.getId(), e);
                    }
                }
                MustacheOrgConfigView.enrich(model);
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String validate(Organisation org) {
        if (OrgConfig.hostOf(org.getBaseUrl()) == null
                || org.getName() == null
                || org.getName().isBlank()
                || org.getSuperUserEmail() == null
                || !EMAIL.matcher(org.getSuperUserEmail()).matches()) {
            return "invalid";
        }
        Organisation byBaseUrl = organisationDao.findByBaseUrl(org.getBaseUrl());
        if (byBaseUrl != null && !byBaseUrl.getId().equals(org.getId())) {
            return "baseUrlTaken";
        }
        return null;
    }

    /** Platform administrators (params.properties {@code admins}) only. */
    private Response requirePlatformAdmin() {
        return user != null && user.isPlatformAdmin()
                ? null
                : Response.status(Response.Status.FORBIDDEN)
                        .entity("platform admin required")
                        .type(MediaType.TEXT_PLAIN)
                        .build();
    }

    /** Platform admin, or club ADMIN for the organisation currently in request scope. */
    private Response requireOrgAdminOrPlatform(Organisation org) {
        if (user != null && user.isPlatformAdmin()) {
            return null;
        }
        if (user != null
                && user.isAdmin()
                && org.getId() != null
                && org.getId().equals(OrgScope.currentOrgId())) {
            return null;
        }
        return Response.status(Response.Status.FORBIDDEN)
                .entity("organisation admin required")
                .type(MediaType.TEXT_PLAIN)
                .build();
    }

    Response withOrgScope(Organisation org, ResponseSupplier action) {
        RequestContext ctx = RequestContext.require();
        Organisation previous = ctx.organisation();
        ctx.setOrganisation(org);
        try {
            return action.get();
        } finally {
            ctx.setOrganisation(previous);
        }
    }

    void withOrgScope(Organisation org, IoRunnable action) throws IOException {
        RequestContext ctx = RequestContext.require();
        Organisation previous = ctx.organisation();
        ctx.setOrganisation(org);
        try {
            action.run();
        } finally {
            ctx.setOrganisation(previous);
        }
    }

    private Response redirectToEdit(Organisation org, String queryName, String queryValue, String tab) {
        return Response.seeOther(editRedirectUri(org, queryName, queryValue, tab)).build();
    }

    private Response jsonRedirectToEdit(Organisation org, String queryName, String queryValue, String tab) {
        String json = new JSONObject()
                .put("redirect", editRedirectUri(org, queryName, queryValue, tab).toString())
                .toString();
        return Response.ok(json).build();
    }

    private URI editRedirectUri(Organisation org, String queryName, String queryValue, String tab) {
        return Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                .path("admin/organisations")
                .path(String.valueOf(org.getId()))
                .queryParam(queryName, queryValue)
                .queryParam("tab", tab)
                .fragment(tab)
                .build());
    }

    private static String formValue(MultivaluedMap<String, String> form, String key) {
        return form == null ? null : form.getFirst(key);
    }

    private static void putIfBlank(Map<String, String> map, String key, String value) {
        if (blankToNull(map.get(key)) == null && blankToNull(value) != null) {
            map.put(key, value);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (blankToNull(a) != null) {
            return a;
        }
        return b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @FunctionalInterface
    interface IoRunnable {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface IoConsumer<T> {
        void accept(T value) throws IOException;
    }

    @FunctionalInterface
    interface ResponseSupplier {
        Response get();
    }
}
