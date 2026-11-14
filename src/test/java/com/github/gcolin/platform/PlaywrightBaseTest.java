package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.eclipse.jetty.ee11.webapp.WebAppContext;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class PlaywrightBaseTest {

    protected Playwright playwright;
    protected Browser browser;
    private Server server;
    protected BrowserContext browserContext;
    protected static final String BASE_URL = "http://localhost:8080";
    protected int port;
    private String previousConfigDir;
    private String previousTestProperty;
    private String previousLoadtestProperty;
    private Path tempConfigDir;

    @BeforeEach
    public void setup() throws Exception {
        previousTestProperty = System.getProperty("test");
        previousLoadtestProperty = System.getProperty("loadtest");
        System.setProperty("test", "true");
        System.setProperty("loadtest", "true");
        previousConfigDir = System.getProperty("CONFIG_DIR");
        // Isolate CONFIG_DIR: OrganisationBootstrap migrates club/stripe keys out of params.properties.
        // A temp copy keeps src/test/resources/params.properties intact across the suite.
        tempConfigDir = Files.createTempDirectory("ticketchess-it-config");
        Path sourceParams =
                Path.of("src/test/resources/params.properties").toAbsolutePath().normalize();
        Files.copy(sourceParams, tempConfigDir.resolve("params.properties"));
        System.setProperty("CONFIG_DIR", tempConfigDir.toAbsolutePath().normalize().toString());
        server = new Server(8080);
        WebAppContext context = new WebAppContext();
        context.setContextPath("/");
        context.setBaseResourceAsString("src/main/resources/webapp");
        context.setParentLoaderPriority(true);
        server.setHandler(context);
        server.start();
        port = 8080;
        boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));

        browserContext = browser.newContext(
                new Browser.NewContextOptions().setLocale("fr-FR").setTimezoneId("Europe/Paris"));
        File emails = new File("emails");
        if (emails.exists()) {
            for (File f : emails.listFiles()) {
                f.delete();
            }
        }
    }

    public void login(Page page) {
        login(page, BASE_URL);
    }

    public void login(Page page, String baseUrl) {
        page.navigate(baseUrl);
        Locator bouton = page.locator("#googleauth");
        assertTrue(bouton.isVisible());
        bouton.click();
        page.waitForSelector("#navbarDropdown");
    }

    protected String getBaseUrl(String url) {
        return url.split("\\?")[0];
    }

    protected void navigateToEventEdit(Page page, String eventId) {
        page.navigate(BASE_URL + "/event/" + eventId + "/edit");
    }

    protected void openEventActionsMenu(Page page) {
        page.locator(".dropdown:has(a.dropdown-item) button[data-bs-toggle='dropdown']").first().click();
    }

    @AfterEach
    public void cleanupContext() throws Exception {
        if (browserContext != null) {
            browserContext.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (server != null) {
            server.stop();
            server.destroy();
        }
        AppContext.shutdown();
        restoreProperty("CONFIG_DIR", previousConfigDir);
        restoreProperty("test", previousTestProperty);
        restoreProperty("loadtest", previousLoadtestProperty);
        deleteRecursively(tempConfigDir);
        tempConfigDir = null;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
