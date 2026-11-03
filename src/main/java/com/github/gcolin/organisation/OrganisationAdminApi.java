package com.github.gcolin.organisation;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.membership.License;
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
import jakarta.persistence.EntityManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequireRole(RoleCode.ADMIN)
@Path("admin/organisations")
public class OrganisationAdminApi {

    private static final Logger logger = LoggerFactory.getLogger(OrganisationAdminApi.class);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,98}[a-z0-9]|[a-z0-9]");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** Club / invoice / stripe settings (moved from {@code AdminApi}). */
    static final Map<String, Set<String>> SETTINGS_TAB_KEYS = Map.of(
            "club",
            Set.of(
                    "title",
                    "contact.url",
                    "source.url",
                    "org.name",
                    "org.email",
                    "org.address",
                    "org.hosting.address",
                    "membership.notif.emails",
                    "mail.USER_NAME",
                    "mail.PASSWORD"),
            "invoice",
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
                    "invoice.donation.prefix"),
            "stripe",
            Set.of(
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
            "payment.transfer.memberships");

    private static final Set<String> SETTINGS_TABS = Set.of("club", "invoice", "files", "stripe", "appearance");

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
        return Response.ok(form(new Organisation(), null, null, "identity", "", "")).build();
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
        return Response.ok(form(org, null, null, tab, success, error)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(
            @FormParam("slug") String slug,
            @FormParam("base_url") String baseUrl,
            @FormParam("name") String name,
            @FormParam("super_user_email") String superUserEmail,
            @FormParam("cloneFromOrgId") String cloneFromOrgId) {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        Organisation org = new Organisation();
        applyIdentity(org, slug, baseUrl, name, superUserEmail);
        Integer sourceId = parseId(cloneFromOrgId);

        String error = validate(org);
        if (error != null) {
            return Response.ok(form(org, sourceId, error, "identity", "", "")).build();
        }

        Organisation source = sourceId == null ? null : organisationDao.find(sourceId);
        if (source != null) {
            copySettings(source, org);
        }
        organisationDao.persist(org);
        RequestContext.require().em().flush();
        if (source != null) {
            cloneData(source.getId(), org.getId());
        }
        AppContext.get().organisationResolver().invalidate();
        logger.info("Created organisation {} ({})", org.getSlug(), org.getBaseUrl());
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
            @FormParam("slug") String slug,
            @FormParam("base_url") String baseUrl,
            @FormParam("name") String name,
            @FormParam("super_user_email") String superUserEmail,
            @FormParam("active") String active) {
        Response denied = requirePlatformAdmin();
        if (denied != null) {
            return denied;
        }
        Organisation org = organisationDao.find(id);
        if (org == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        applyIdentity(org, slug, baseUrl, name, superUserEmail);
        org.setActive("true".equalsIgnoreCase(active) || "on".equalsIgnoreCase(active));

        String error = validate(org);
        if (error != null) {
            return Response.ok(form(org, null, error, "club", "", "")).build();
        }

        RequestContext.require().em().flush();
        AppContext.get().organisationResolver().invalidate();
        logger.info("Updated organisation {} ({})", org.getSlug(), org.getBaseUrl());
        return Response.seeOther(Redirects.toSameOriginRelative(uriInfo.getBaseUriBuilder()
                        .path("admin/organisations")
                        .path(String.valueOf(org.getId()))
                        .queryParam("success", "updated")
                        .queryParam("tab", "club")
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
        String tab = formValue(form, "tab");
        if (tab == null || !SETTINGS_TAB_KEYS.containsKey(tab)) {
            return redirectToEdit(org, "error", "configInvalid", "club");
        }
        if ("club".equals(tab) && user != null && user.isPlatformAdmin()) {
            String name = formValue(form, "org.name");
            applyIdentity(
                    org,
                    org.getSlug(),
                    formValue(form, "base_url"),
                    name != null && !name.isBlank() ? name : org.getName(),
                    formValue(form, "super_user_email"));
            String active = formValue(form, "active");
            org.setActive("true".equalsIgnoreCase(active) || "on".equalsIgnoreCase(active));
            String identityError = validate(org);
            if (identityError != null) {
                return Response.ok(form(org, null, identityError, "club", "", "")).build();
            }
            RequestContext.require().em().flush();
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
        if ("stripe".equals(tab)) {
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
        }
        try {
            withOrgScope(org, () -> {
                if ("stripe".equals(tab) && !ribService.exists()) {
                    updates.put("payment.transfer.events", "false");
                    updates.put("payment.transfer.memberships", "false");
                }
                config.updateProperties(updates);
                if ("club".equals(tab) && sendMail != null) {
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

    private static void applyIdentity(
            Organisation org, String slug, String baseUrl, String name, String superUserEmail) {
        org.setSlug(slug == null ? null : slug.trim().toLowerCase(Locale.ROOT));
        org.setBaseUrl(OrgConfig.normalizeBaseUrl(baseUrl));
        org.setName(name == null ? null : name.trim());
        org.setSuperUserEmail(superUserEmail == null ? null : superUserEmail.trim().toLowerCase(Locale.ROOT));
    }

    private MustacheHtml form(
            Organisation organisation,
            Integer cloneFromOrgId,
            String identityError,
            String tab,
            String success,
            String queryError) {
        Map<String, Object> model = new HashMap<>();
        boolean editMode = organisation != null && organisation.getId() != null;
        boolean canEditIdentity = user != null && user.isPlatformAdmin();
        model.put("organisation", organisation != null ? organisation : new Organisation());
        model.put("editMode", editMode);
        model.put("canEditIdentity", canEditIdentity);
        model.put("titleKey", editMode ? "page.organisationEdit" : "page.organisationNew");
        model.put("activePageTitleKey", editMode ? "page.organisationEdit" : "page.organisationNew");
        List<Map<String, Object>> orgRows = new java.util.ArrayList<>();
        if (!editMode) {
            for (Organisation source : organisationDao.allOrdered()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", source.getId());
                row.put("name", source.getName());
                row.put("slug", source.getSlug());
                row.put("cloneSelected", cloneFromOrgId != null && cloneFromOrgId.equals(source.getId()));
                orgRows.add(row);
            }
        }
        model.put("organisations", orgRows);
        model.put("cloneFromOrgId", cloneFromOrgId);

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
        String effectiveTab = tab == null || tab.isBlank() ? null : tab.trim();
        if (effectiveTab == null
                || "identity".equals(effectiveTab)
                || !SETTINGS_TABS.contains(effectiveTab)) {
            effectiveTab = "club";
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

                OrgConfig orgConfig = new OrgConfig(organisation);
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
                secrets.put(
                        "oauth.clientSecret", blankToNull(orgConfig.getProperty("oauth.clientSecret")) != null);
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
        if (org.getSlug() == null
                || !SLUG.matcher(org.getSlug()).matches()
                || OrgConfig.hostOf(org.getBaseUrl()) == null
                || org.getName() == null
                || org.getName().isBlank()
                || org.getSuperUserEmail() == null
                || !EMAIL.matcher(org.getSuperUserEmail()).matches()) {
            return "invalid";
        }
        Organisation bySlug = organisationDao.findBySlug(org.getSlug());
        if (bySlug != null && !bySlug.getId().equals(org.getId())) {
            return "slugTaken";
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

    /** Copies non-secret settings: Stripe options, OAuth endpoints and the settings JSON. */
    private static void copySettings(Organisation from, Organisation to) {
        to.setStripeKeyprefix(from.getStripeKeyprefix());
        to.setStripeSimuled(from.isStripeSimuled());
        to.setStripeCardEvents(from.isStripeCardEvents());
        to.setStripeCardMemberships(from.isStripeCardMemberships());
        to.setPaymentTransferEvents(from.isPaymentTransferEvents());
        to.setPaymentTransferMemberships(from.isPaymentTransferMemberships());
        to.setOauthAuthorizationUrl(from.getOauthAuthorizationUrl());
        to.setOauthTokenUrl(from.getOauthTokenUrl());
        to.setOauthScope(from.getOauthScope());
        to.setOauthLogoutUrl(from.getOauthLogoutUrl());
        to.setOauthAccountUrl(from.getOauthAccountUrl());
        to.setOauthUserinfoUrl(from.getOauthUserinfoUrl());
        to.setSettingsJson(from.getSettingsJson());
    }

    /** Copies licenses and club seasons; the org filter is lifted to read the source organisation. */
    private void cloneData(int fromOrgId, int toOrgId) {
        EntityManager em = RequestContext.require().em();
        OrgScope.disableFilter(em);
        try {
            List<License> licenses = em.createQuery("SELECT l FROM License l WHERE l.orgId = :org", License.class)
                    .setParameter("org", fromOrgId)
                    .getResultList();
            for (License source : licenses) {
                License copy = new License(source.getName());
                copy.setAccessRule(source.getAccessRule());
                copy.setOrgId(toOrgId);
                em.persist(copy);
            }
            List<ClubSeason> seasons = em.createQuery("SELECT s FROM ClubSeason s WHERE s.orgId = :org", ClubSeason.class)
                    .setParameter("org", fromOrgId)
                    .getResultList();
            for (ClubSeason source : seasons) {
                ClubSeason copy = new ClubSeason();
                copy.setName(source.getName());
                copy.setStartDate(source.getStartDate());
                copy.setEndDate(source.getEndDate());
                copy.setCurrent(source.isCurrent());
                copy.setOrgId(toOrgId);
                em.persist(copy);
            }
            em.flush();
        } finally {
            Integer currentOrgId = OrgScope.currentOrgId();
            if (currentOrgId != null) {
                OrgScope.enableFilter(em, currentOrgId);
            }
        }
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
        return Response.ok(Map.of("redirect", editRedirectUri(org, queryName, queryValue, tab).toString()))
                .build();
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

    private static Integer parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
