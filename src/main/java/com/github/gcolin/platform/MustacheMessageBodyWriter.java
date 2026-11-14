package com.github.gcolin.platform;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.payment.RibService;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Produces(MediaType.TEXT_HTML + ";charset=UTF-8")
public class MustacheMessageBodyWriter implements MessageBodyWriter<MustacheHtml> {

    private static final Logger logger = LoggerFactory.getLogger(MustacheMessageBodyWriter.class);

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == MustacheHtml.class;
    }

    @Override
    public void writeTo(
            MustacheHtml t,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream)
            throws IOException, WebApplicationException {
        RequestContext ctx = RequestContext.require();
        HttpServletRequest request = ctx.request();
        LoggedUser user = ctx.loggedUser();
        StateService state = ctx.stateService();
        Config config = AppContext.get().config();
        RibService ribService = AppContext.get().ribService();
        MustacheConfig mustacheConfig = AppContext.get().mustacheConfig();

        Messages messages = new Messages(request.getLocale());
        Map<String, Object> model = new HashMap<>();
        model.putAll(t.getModel());
        model.put("msg", messages);
        model.put("i18n", messages.asLambda());
        model.put("contextPath", request.getContextPath());
        model.put("user", user);
        model.put("state", state);
        Page pageConfig = config.pageForRequest();
        model.put("pageConfig", pageConfig);
        model.put("navbarBrand", resolveNavbarBrand(pageConfig));
        String bg = pageConfig.getBackgroundUrl();
        model.put("backgroundUrl", bg != null && !bg.isBlank() ? bg : "/assets/6.jpg");
        model.put("ribAvailable", ribService != null && ribService.exists());
        if (user != null) {
            double eventDebt = user.getEventDebt();
            double membershipDebt = user.getMembershipDebt();
            model.put("hasEventDebt", eventDebt > 0);
            model.put("hasMembershipDebt", membershipDebt > 0);
            model.put("eventDebtFormatted", String.format(java.util.Locale.US, "%.2f", eventDebt));
            model.put("membershipDebtFormatted", String.format(java.util.Locale.US, "%.2f", membershipDebt));
            boolean showEventActionsMenu = user.canViewEventConfig()
                    || user.canManageEvents()
                    || user.isAdmin()
                    || Boolean.TRUE.equals(model.get("showView"));
            model.put("showEventActionsMenu", showEventActionsMenu);
        }
        Object page = model.get("page");
        if (page instanceof String pageName) {
            model.put(pageName + "Active", true);
        }
        if (!model.containsKey("title") || model.get("title") == null || String.valueOf(model.get("title")).isBlank()) {
            Object titleKey = model.get("titleKey");
            if (titleKey instanceof String key && !key.isBlank()) {
                String title = messages.get(key);
                if (model.get("titleSuffix") instanceof String suffix && !suffix.isBlank()) {
                    title = title + " \u2014 " + suffix;
                }
                model.put("title", title);
            } else if (pageConfig.getTitle() != null) {
                model.put("title", pageConfig.getTitle());
            }
        }

        Object status = model.get("status");
        if (status instanceof String statusValue && !statusValue.isBlank()) {
            model.put("statusIs" + statusValue, true);
            model.put("statusIsDRAFT", "DRAFT".equals(statusValue));
            model.put("statusIsACTIVE", "ACTIVE".equals(statusValue));
            model.put("statusIsONGOING", "ONGOING".equals(statusValue));
            model.put("statusIsCOMPLETED", "COMPLETED".equals(statusValue));
        }

        Object success = model.get("success");
        if (success instanceof String successValue) {
            model.put("successRegister", "register".equals(successValue));
            model.put("successConfigSaved", "configSaved".equals(successValue));
            model.put("successPending", "pending".equals(successValue));
            model.put("successPayment", "payment".equals(successValue));
            if (!successValue.isBlank()) {
                String camel = successValue.substring(0, 1).toUpperCase() + successValue.substring(1);
                model.put("successIs" + camel, true);
            }
        }

        Object error = model.get("error");
        if (error instanceof String errorValue && !errorValue.isBlank()) {
            String camel = errorValue.substring(0, 1).toUpperCase() + errorValue.substring(1);
            model.put("errorIs" + camel, true);
        }

        Object missingPlayers = model.get("missingPlayers");
        if (missingPlayers instanceof java.util.Collection<?> missing) {
            model.put("hasMissingPlayers", !missing.isEmpty());
            if (!missing.isEmpty() && !model.containsKey("missingPlayersJoined")) {
                model.put(
                        "missingPlayersJoined",
                        missing.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
            }
            if (Boolean.TRUE.equals(model.get("hasMissingPlayers"))) {
                model.put(
                        "missingPlayersErrorText",
                        messages.get("event.playerLoadError", model.get("missingPlayersJoined")));
            }
        }
        if (Boolean.TRUE.equals(model.get("hasRemainingSubscriptions"))
                && model.get("remainingSubscriptions") instanceof Number remaining) {
            model.put("remainingSubscriptionsText", messages.get("event.remainingSubscriptions", remaining));
        }
        if (Boolean.TRUE.equals(model.get("hasCollectionRemaining"))
                && model.get("collectionRemainingSubscriptions") instanceof Number remaining) {
            model.put(
                    "collectionRemainingSubscriptionsText",
                    messages.get("event.collectionRemainingSubscriptions", remaining));
        }
        if (model.get("pendingEvents") instanceof java.util.Collection<?> pendingEvents) {
            for (Object pendingEvent : pendingEvents) {
                if (pendingEvent instanceof com.github.gcolin.event.Event ev && ev.getPlayers() != null) {
                    for (com.github.gcolin.player.DisplayPlayer pl : ev.getPlayers()) {
                        Integer ahead = pl.getPendingQueueAhead();
                        pl.setPendingQueueAheadText(
                                messages.get("myevents.pendingQueue.ahead", ahead != null ? ahead : 0));
                    }
                }
            }
        }
        if (Boolean.TRUE.equals(model.get("showReplaceResult"))) {
            int replacedCount = model.get("replacedCount") instanceof Number n ? n.intValue() : 0;
            int failedCount = model.get("failedCount") instanceof Number n ? n.intValue() : 0;
            model.put("replaceResultText", messages.get("playersub.replaceResult", replacedCount, failedCount));
        }
        if (Boolean.TRUE.equals(model.get("showDeleteResult"))) {
            int deletedCount = model.get("deletedCount") instanceof Number n ? n.intValue() : 0;
            int deleteFailedCount = model.get("deleteFailedCount") instanceof Number n ? n.intValue() : 0;
            model.put("deleteResultText", messages.get("playersub.deleteResult", deletedCount, deleteFailedCount));
        }
        if (model.get("rowCount") instanceof Number rowCount && model.get("replaceableCount") instanceof Number replaceableCount) {
            model.put(
                    "playersSubCountsText",
                    messages.get("playersub.counts", rowCount.intValue(), replaceableCount.intValue()));
        }
        if (model.get("pendingRowCount") instanceof Number pendingRowCount) {
            model.put("playersSubPendingCountsText", messages.get("playersub.pendingCounts", pendingRowCount.intValue()));
        }
        if (model.get("orphanCount") instanceof Number orphanCount) {
            model.put("playersSubOrphanCountsText", messages.get("playersub.orphanCustomPlayers", orphanCount.intValue()));
        }
        Object activePageTitleKey = model.get("activePageTitleKey");
        if (activePageTitleKey instanceof String key && !key.isBlank() && !model.containsKey("activePageTitle")) {
            model.put("activePageTitle", messages.get(key));
        }
        Object clubRegisterTitleKey = model.get("clubRegisterTitleKey");
        if (clubRegisterTitleKey instanceof String key && !key.isBlank()) {
            String seasonName = model.get("clubSeasonName") instanceof String s ? s : "";
            model.put("clubRegisterTitleText", messages.get(key, seasonName));
        }
        Object headingKey = model.get("headingKey");
        if (headingKey instanceof String key && !key.isBlank() && !model.containsKey("headingText")) {
            Object headingParam = model.get("headingParam");
            model.put(
                    "headingText",
                    headingParam != null ? messages.get(key, headingParam) : messages.get(key));
        }
        Object clubRegisterDescriptionKey = model.get("clubRegisterDescriptionKey");
        if (clubRegisterDescriptionKey instanceof String key && !key.isBlank()) {
            String seasonName = model.get("clubSeasonName") instanceof String s ? s : "";
            model.put("clubRegisterDescriptionText", messages.get(key, seasonName));
        }
        if (model.get("sent") instanceof Number sentCount) {
            model.put("sentMessageText", messages.get("mail.sent", sentCount));
        }
        if (model.get("sessionCount") instanceof Number sessionCount) {
            model.put("sessionCountText", messages.get("loggedUsers.sessionCount", sessionCount));
        }
        if (model.get("errors") instanceof java.util.Collection<?> errors) {
            model.put("hasErrors", !errors.isEmpty());
        }
        if (model.containsKey("statusCode") && model.containsKey("statusMessage")) {
            if (!model.containsKey("title") || model.get("title") == null || String.valueOf(model.get("title")).isBlank()) {
                model.put("title", model.get("statusCode") + " \u2013 " + model.get("statusMessage"));
            }
        }
        if (t.getTemplate() != null && t.getTemplate().contains("mentions")) {
            LegalMustacheEnricher.enrich(model, messages, pageConfig);
        }
        if (model.get("errorMessageKey") instanceof String errorKey && !errorKey.isBlank()) {
            model.put("errorMessageText", messages.get(errorKey));
        }
        if ("payment.payer.editTitle".equals(model.get("payerTitleKey"))
                && model.get("payerTitleParam") != null) {
            model.put("payerTitleText", messages.get("payment.payer.editTitle", model.get("payerTitleParam")));
        }
        if (model.containsKey("total") && model.containsKey("byClub")) {
            com.github.gcolin.event.StatisticsMustacheEnricher.enrichReport(model, messages);
        }
        if (model.containsKey("totalPayments") && model.containsKey("closestEvents")) {
            com.github.gcolin.event.StatisticsMustacheEnricher.enrichDashboard(model, messages);
        }
        if (model.containsKey("eventCollections")) {
            com.github.gcolin.event.EventCollectionMustacheEnricher.enrichListPage(model, messages);
        }
        if (model.containsKey("eventCollection") && model.containsKey("createMode")) {
            com.github.gcolin.event.EventCollectionMustacheEnricher.enrichEditPage(model, messages);
        }
        if (model.get("groups") instanceof java.util.List<?> groupNodes
                && (groupNodes.isEmpty() || groupNodes.get(0) instanceof com.github.gcolin.event.EventAdminGroupNode)) {
            @SuppressWarnings("unchecked")
            java.util.List<com.github.gcolin.event.EventAdminGroupNode> groups =
                    (java.util.List<com.github.gcolin.event.EventAdminGroupNode>) groupNodes;
            com.github.gcolin.event.EventAdminMustacheEnricher.enrich(model, groups, messages, user);
        }
        if (model.containsKey("mismatches")) {
            com.github.gcolin.payment.PaymentAuditMustacheEnricher.enrich(model, messages);
        }
        if (model.containsKey("payments") && model.containsKey("paymentSearchLicences")) {
            com.github.gcolin.payment.PaymentMustacheEnricher.enrichListPage(model, messages);
        }
        if (model.containsKey("paymentStatuses") && model.containsKey("paymentTypes")) {
            com.github.gcolin.payment.PaymentMustacheEnricher.enrichEditPage(model, messages);
        }
        if (model.containsKey("sub")
                && model.containsKey("options")
                && t.getTemplate() != null
                && t.getTemplate().contains("subEdit")) {
            com.github.gcolin.registration.RegistrationMustacheEnricher.enrichSubEdit(model);
        }
        if (model.containsKey("isNew")
                && model.containsKey("sub")
                && t.getTemplate() != null
                && t.getTemplate().contains("subOptionEdit")) {
            com.github.gcolin.registration.RegistrationMustacheEnricher.enrichSubOptionEdit(model);
        }
        if (model.containsKey("playerRows")
                && model.containsKey("totalItems")
                && model.containsKey("currentPage")
                && model.containsKey("totalPages")) {
            model.put(
                    "paginationPlayerCountText",
                    messages.get(
                            "pagination.playerCount",
                            model.get("totalItems"),
                            model.get("currentPage"),
                            model.get("totalPages")));
        }
        if (model.containsKey("manualPlayersFile") && model.get("safePlayers") instanceof java.util.Collection<?> safePlayers) {
            model.put("manualPlayerCountText", messages.get("databasePlayers.playerCount", safePlayers.size()));
        }

        String templateRef = t.getTemplate();
        if (templateRef != null) {
            if (templateRef.contains("event/eventEdit")) {
                com.github.gcolin.event.EventMustacheEnricher.enrichEditPage(model, messages);
            } else if (templateRef.endsWith("event/event.mustache") || "event/event".equals(templateRef)) {
                com.github.gcolin.event.EventMustacheEnricher.enrichPublicEventPage(model, messages);
            } else if (templateRef.contains("registration/register")) {
                com.github.gcolin.event.EventMustacheEnricher.enrichRegisterPage(model, messages);
            } else if (templateRef.contains("registration/clubRegister")) {
                com.github.gcolin.registration.ClubRegisterMustacheEnricher.enrich(model, messages, user);
            } else if (templateRef.contains("registration/clubMembershipOptions")) {
                com.github.gcolin.registration.ClubRegisterMustacheEnricher.enrichMembershipOptionsPage(model, messages);
            } else if (templateRef.contains("event/myevents")) {
                com.github.gcolin.event.EventMustacheEnricher.enrichMyEventsPage(model, messages);
            } else if (templateRef.endsWith("event/events.mustache") || "event/events".equals(templateRef)) {
                com.github.gcolin.event.EventMustacheEnricher.enrichEventsListPage(model, messages);
            } else if (templateRef.contains("playersubscriptionAdmin")) {
                com.github.gcolin.registration.RegistrationMustacheEnricher.enrichPlayerSubscriptionAdminPage(
                        model, messages);
            }
        }

        // Labels that depend on keys set by enrichers above.
        Object registerActionKey = model.get("registerActionKey");
        if (registerActionKey instanceof String key && !key.isBlank()) {
            model.put("registerActionLabel", messages.get(key));
        }
        Object selectActionKey = model.get("selectActionKey");
        if (selectActionKey instanceof String key && !key.isBlank()) {
            model.put("selectActionLabel", messages.get(key));
        }
        Object fullMessageKey = model.get("fullMessageKey");
        if (fullMessageKey instanceof String key && !key.isBlank()) {
            model.put("fullMessageText", messages.get(key));
        }

        String templateName = t.getTemplate();
        if (!templateName.endsWith(".mustache")) {
            templateName = templateName + ".mustache";
        }

        MustacheFactory factory = mustacheConfig.getFactory();
        Mustache mustache = factory.compile(templateName);
        Writer writer = new OutputStreamWriter(entityStream, StandardCharsets.UTF_8);
        try {
            mustache.execute(writer, model).flush();
        } catch (RuntimeException e) {
            if (ClientDisconnect.isGone(e)) {
                return;
            }
            logger.error("Failed to render template {}", templateName, e);
            throw new WebApplicationException(e);
        } catch (IOException e) {
            if (ClientDisconnect.isGone(e)) {
                return;
            }
            throw e;
        }
    }

    /** Brand text for the navbar: current organisation name, else page org/title. */
    static String resolveNavbarBrand(Page page) {
        Organisation org = OrgScope.currentOrg();
        if (org != null && org.getName() != null && !org.getName().isBlank()) {
            return org.getName().trim();
        }
        if (page != null) {
            if (page.getOrgName() != null && !page.getOrgName().isBlank()) {
                return page.getOrgName().trim();
            }
            if (page.getTitle() != null && !page.getTitle().isBlank()) {
                return page.getTitle().trim();
            }
        }
        return "TicketChess";
    }
}
