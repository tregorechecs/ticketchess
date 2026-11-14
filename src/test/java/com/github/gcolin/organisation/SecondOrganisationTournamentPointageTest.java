package com.github.gcolin.organisation;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.PlaywrightBaseTest;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class SecondOrganisationTournamentPointageTest extends PlaywrightBaseTest {

    @Test
    public void createSecondOrganisationThenTournamentThenPointage() {
        Page page = browserContext.newPage();
        login(page);

        String orgBase = "http://club2.localhost:8080";
        page.navigate(BASE_URL + "/admin/organisations/new");
        page.locator("#org_name").fill("Club Two");
        page.locator("#org_base_url").fill(orgBase);
        page.locator("#org_super_user_email").fill("test@test.com");
        page.locator("button[type='submit']").click();
        page.waitForURL("**/admin/organisations?success=created**");
        assertThat(page.locator("table.table")).containsText("Club Two");
        assertThat(page.locator("table.table")).containsText(orgBase);

        // remember_me cookie is host-scoped; re-auth on the second organisation host
        login(page, orgBase);
        page.navigate(orgBase + "/event/new");

        Locator name = page.locator("#event_name");
        assertThat(name).isVisible();
        name.fill("Tournoi Club Two");

        LocalDate now = LocalDate.now();
        int seasonStartYear = now.getMonthValue() >= 9 ? now.getYear() : now.getYear() - 1;
        String eventDate = LocalDate.of(seasonStartYear, 10, 15).toString();
        page.locator("#event_startdate").fill(eventDate);
        page.locator("#event_enddate").fill(eventDate);
        page.selectOption("#event_type", new SelectOption().setLabel("Blitz"));
        page.locator("#event_price").fill("10");
        page.locator("#event_price_young").fill("5");
        page.locator("#event_submit").click();

        page.waitForURL("**/event/*/edit**");
        String eventId = page.url().replaceAll(".*/event/(\\d+)/edit.*", "$1");
        assertTrue(eventId.matches("\\d+"), "event id expected in edit URL: " + page.url());

        page.selectOption("#event_status", new SelectOption().setLabel("Inscriptions ouvertes"));
        page.locator("#event_submit").click();
        page.waitForLoadState();

        Locator optionsTab = page.locator("#options-tab");
        assertThat(optionsTab).isVisible();
        optionsTab.click();
        Locator pointage = page.locator("#event_pointage");
        assertThat(pointage).isVisible();
        pointage.check();
        page.locator("#event_options_submit").click();
        page.waitForURL("**/event/" + eventId + "/edit**");
        assertThat(page.locator(".alert-success:not(.d-none)")).isVisible();

        optionsTab = page.locator("#options-tab");
        optionsTab.click();
        assertTrue(page.locator("#event_pointage").isChecked());

        page.navigate(orgBase + "/event/" + eventId);
        Locator register = page.locator("#register");
        assertThat(register).isVisible();
        register.click();
        page.waitForURL("**/event/" + eventId + "/register");

        page.locator("#player").fill("Gael colin");
        page.locator("#searchplayer").click();
        page.waitForURL("**/event/" + eventId + "/register?query=**");
        Locator selectPlayer = page.locator(".btn.btn-secondary.js-select").first();
        assertThat(selectPlayer).isVisible();
        selectPlayer.click();
        page.waitForURL("**/event/my**");

        page.navigate(orgBase + "/event/" + eventId);
        openEventActionsMenu(page);
        Locator desk = page.locator("#desk");
        assertThat(desk).isVisible();
        desk.click();
        page.waitForURL("**/event/" + eventId + "/desk");
        assertThat(page.locator("#desk-table")).isVisible();

        page.waitForFunction(
                "() => { const t = (document.getElementById('desk-status').textContent || '').toLowerCase(); return t.includes('en ligne') || t.includes('online'); }",
                null,
                new Page.WaitForFunctionOptions().setTimeout(15000));

        Locator attendance = page.locator(".js-desk-attendance").first();
        assertThat(attendance).isVisible();
        assertThat(attendance).not().isChecked();
        attendance.click();
        page.waitForTimeout(800);
        assertThat(attendance).isChecked();

        page.reload();
        page.waitForFunction(
                "() => { const t = (document.getElementById('desk-status').textContent || '').toLowerCase(); return t.includes('en ligne') || t.includes('online'); }",
                null,
                new Page.WaitForFunctionOptions().setTimeout(15000));
        assertThat(page.locator(".js-desk-attendance").first()).isChecked();

        page.close();
    }
}
