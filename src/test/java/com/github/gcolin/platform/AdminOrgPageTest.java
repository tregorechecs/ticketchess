package com.github.gcolin.platform;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class AdminOrgPageTest extends PlaywrightBaseTest {

    @Test
    public void saveClubTabShouldKeepCurrentConfig() {
        Page page = browserContext.newPage();
        try {
            login(page);
            page.navigate(BASE_URL + "/admin/organisations/1?tab=identity");

            assertEquals(BASE_URL + "/admin/organisations/1", getBaseUrl(page.url()));
            assertThat(page.locator("h1")).containsText("organisation");

            Locator orgName = page.locator("#identity input[name='org.name']");
            Locator title = page.locator("#identity input[name='title']");
            assertTrue(orgName.inputValue().length() > 0, "org.name should be prefilled");
            assertTrue(title.inputValue().length() > 0, "title should be prefilled");

            String updatedName = orgName.inputValue() + " Playwright";
            orgName.fill(updatedName);
            page.locator("#identity button[type='submit']").click();
            page.waitForLoadState();

            assertTrue(page.url().contains("success=configSaved"), page.url());
            assertThat(page.locator(".alert-success")).containsText("enregist");
            assertEquals(updatedName, page.locator("#identity input[name='org.name']").inputValue());
        } finally {
            page.close();
        }
    }
}
