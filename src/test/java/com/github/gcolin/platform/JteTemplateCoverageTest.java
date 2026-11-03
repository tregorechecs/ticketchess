package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Playwright smoke tests: every HTTP-rendered Mustache page template must return HTML without a 500 error.
 * Email templates ({@code tmpl/}) and partials ({@code head}, {@code menubar}, {@code footer}) are covered
 * by {@link MailTemplateTest} and {@link MustacheCompileTest} respectively.
 * <p>
 * Templates needing runtime-created entities (membership edit, payment edit, payment payer, event-collection
 * variant/mail/stats) reuse the same {@code .mustache} file as their {@code /new} counterparts where possible, or
 * create seed data in the test (payment payer) once data exists in CI.
 */
@Tag("integration")
public class JteTemplateCoverageTest extends PlaywrightBaseTest {

    record TemplatePage(String template, String path) {}

    @Test
    void allPageTemplatesRender() {
        Page page = browserContext.newPage();
        page.setDefaultTimeout(15_000);

        page.navigate(BASE_URL + "/auth-sim");
        page.waitForLoadState();

        String paymentPayerPath = createOwnedPaidPaymentPayerPath(page);

        List<TemplatePage> failures = new ArrayList<>();
        for (TemplatePage tp : pageTemplates(paymentPayerPath)) {
            if (!tryRender(page, tp)) {
                failures.add(tp);
            }
        }

        Page guestPage = browser.newContext(
                        new Browser.NewContextOptions().setLocale("fr-FR").setTimezoneId("Europe/Paris"))
                .newPage();
        guestPage.setDefaultTimeout(15_000);
        TemplatePage notLogged = new TemplatePage("auth/notlogged.mustache", "/event/my");
        if (!tryRender(guestPage, notLogged)) {
            failures.add(notLogged);
        }
        guestPage.context().close();
        page.close();

        assertTrue(
                failures.isEmpty(),
                "JTE templates that failed to render: "
                        + failures.stream().map(tp -> tp.template() + " (" + tp.path() + ")").toList());
    }

    /**
     * {@code /payment/{id}/payer} requires a PAID payment owned by the auth-sim user; seed data payment 1 is not.
     */
    private String createOwnedPaidPaymentPayerPath(Page page) {
        page.navigate(BASE_URL + "/payment/new");
        page.locator("#userEmail").fill("test@test.com");
        page.selectOption("#status", "PAID");
        page.selectOption("#type", "CASH");
        page.locator("#amount").fill("10.00");
        page.locator("#paymentForm button[type='submit']").click();
        page.waitForURL("**/payment/*/edit");
        String url = page.url();
        int paymentIdx = url.indexOf("/payment/");
        int editIdx = url.indexOf("/edit", paymentIdx);
        String id = url.substring(paymentIdx + "/payment/".length(), editIdx);
        return "/payment/" + id + "/payer";
    }

    private List<TemplatePage> pageTemplates(String paymentPayerPath) {
        List<TemplatePage> pages = new ArrayList<>();
        Map<String, String> map = new LinkedHashMap<>();

        map.put("event/events.mustache", "/event");
        map.put("event/events.mustache (group)", "/event/aaa");
        map.put("event/event.mustache", "/event/3");
        map.put("event/eventEdit.mustache", "/event/3/edit");
        map.put("event/eventEdit.mustache (new)", "/event/new");
        map.put("event/myevents.mustache", "/event/my");
        map.put("event/eventStatistics.mustache", "/event/3/statistics");
        map.put("event/statistics.mustache", "/statistics");
        map.put("event/eventMail.mustache", "/event/3/mail");
        map.put("event/eventgroup.mustache", "/eventgroup");
        map.put("event/eventgroupEdit.mustache", "/eventgroup/1");
        map.put("event/eventcollection.mustache", "/eventcollection");
        map.put("event/eventcollectionEdit.mustache", "/eventcollection/new");
        map.put("event/eventAdminTree.mustache", "/admin/events");

        map.put("registration/register.mustache", "/event/1/register");
        map.put("registration/subEdit.mustache", "/event/3/register/1");
        map.put("registration/subOptionEdit.mustache", "/event/3/register/1/option/new");
        map.put("registration/playersubscriptionAdmin.mustache", "/playersubscription-admin");
        map.put("registration/clubRegister.mustache", "/club-register");
        map.put("registration/clubMembershipOptions.mustache",
                "/club-register/membership-options?lastname=Doe&firstname=John&birthdate=2010-10-10");

        map.put("membership/membership.mustache", "/membership");
        map.put("membership/membershipNew.mustache", "/membership/new");
        map.put("membership/membershipOption.mustache", "/membership-option-admin");
        map.put("membership/membershipOptionEdit.mustache", "/membership-option-admin/1/edit");
        map.put("membership/membershipOptionEdit.mustache (new)", "/membership-option-admin/new");
        map.put("membership/licenseAdmin.mustache", "/license-admin");
        map.put("membership/licenseEdit.mustache", "/license-admin/1");
        map.put("membership/licenseEdit.mustache (new)", "/license-admin/new");
        map.put("membership/licensePriceAdmin.mustache", "/license-admin/1/price");
        map.put("membership/licensePriceEdit.mustache", "/license-admin/1/price/new");

        map.put("payment/payments.mustache", "/payment");
        map.put("payment/paymentEdit.mustache", "/payment/new");
        map.put("payment/paymentPayer.mustache", paymentPayerPath);
        map.put("payment/paymentStatus.mustache", "/payment/sim");
        map.put("payment/paymentAudit.mustache", "/payment/audit");

        map.put("platform/admin.mustache", "/admin");
        map.put("platform/adminOrg.mustache", "/admin/org");
        map.put("club/seasonAdmin.mustache", "/admin/seasons");
        map.put("club/seasonEdit.mustache", "/admin/seasons/new");
        map.put("club/seasonEdit.mustache (edit)", "/admin/seasons/1/edit");
        map.put("platform/adminMail.mustache", "/admin/mail");
        map.put("platform/dashboard.mustache", "/dashboard");
        map.put("platform/mentions.mustache", "/mentions");
        map.put("platform/error.mustache", "/admin/seasons/999999/edit");

        map.put("auth/loggedusers.mustache", "/logged-users");
        map.put("auth/logas.mustache", "/logas");
        map.put("auth/userauthorization.mustache", "/user-authorization");

        map.put("organisation/organisationAdmin.mustache", "/admin/organisations");
        map.put("organisation/organisationEdit.mustache", "/admin/organisations/new");

        map.put("player/database.mustache", "/database");
        map.put("player/databasePlayers.mustache", "/database/manual-players");
        map.put("player/players.mustache", "/customplayer");
        map.put("player/customplayer.mustache", "/customplayer/1");

        map.put("desk/eventDesk.mustache", "/event/3/desk");

        for (Map.Entry<String, String> entry : map.entrySet()) {
            pages.add(new TemplatePage(entry.getKey(), entry.getValue()));
        }
        return pages;
    }

    private boolean tryRender(Page page, TemplatePage tp) {
        try {
            assertTemplateRenders(page, tp.path(), tp.template());
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private void assertTemplateRenders(Page page, String path, String templateName) {
        page.navigate(BASE_URL + path);
        page.waitForSelector("body", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED));

        String title = page.title();
        String html = page.content();

        assertFalse(
                title.contains("500") || title.contains("Error 500"),
                templateName + " (" + path + ") – title indicates server error: " + title);
        assertFalse(
                html.contains("HTTP ERROR 500") || html.contains("Failed to render template"),
                templateName + " (" + path + ") – body indicates server error");

        if ("auth/notlogged.mustache".equals(templateName)) {
            assertTrue(
                    html.contains("connect") || html.contains("Connexion") || html.contains("login"),
                    templateName + " should show login prompt");
        } else if ("platform/error.mustache".equals(templateName)) {
            assertTrue(
                    html.contains("error") || html.contains("Error") || html.contains("erreur"),
                    templateName + " should show error content");
        } else {
            assertTrue(
                    page.locator("main").count() > 0 || page.locator("nav").count() > 0,
                    templateName + " (" + path + ") – expected main or nav element");
            assertFalse(html.isBlank(), templateName + " (" + path + ") – empty body");
        }
    }
}
