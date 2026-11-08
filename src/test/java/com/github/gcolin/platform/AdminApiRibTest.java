package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.payment.RibService;
import java.sql.Connection;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminApiRibTest {

    @AfterEach
    void clearRequestContext() {
        RequestContext.close();
    }

    @Test
    void orgPageShouldRedirectMovedTabsToOrganisationEdit() throws Exception {
        AdminApi api = new AdminApi();
        inject(api, "config", configWithProps());
        inject(api, "user", mockUser(false));
        inject(api, "uriInfo", mockUriInfo(URI.create("http://localhost:8080/admin/organisations/1?tab=features")));

        Organisation org = new Organisation();
        org.setId(1);
        org.setName("Club");
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        Response response = api.orgPage("", "", "club");

        assertEquals(303, response.getStatus());
        assertEquals(URI.create("/admin/organisations/1?tab=features"), response.getLocation());
    }

    @Test
    void orgPageShouldRenderOauthTabOnAdminOrg() throws Exception {
        AdminApi api = new AdminApi();
        inject(api, "config", configWithProps());
        inject(api, "user", mockUser(true));
        inject(api, "uriInfo", mockUriInfo(URI.create("http://localhost:8080/admin/org?tab=oauth")));

        Organisation org = new Organisation();
        org.setId(1);
        org.setName("Club");
        RequestContext.openForTest((Connection) null);
        try {
            RequestContext.require().setOrganisation(org);
            Response response = api.orgPage("", "", "oauth");
            assertEquals(200, response.getStatus());
            assertTrue(response.getEntity() instanceof MustacheHtml);
        } finally {
            RequestContext.close();
        }
    }

    @Test
    void orgPageShouldForbidNonPlatformAdmin() throws Exception {
        AdminApi api = new AdminApi();
        inject(api, "config", configWithProps());
        inject(api, "user", mockUser(false));
        inject(api, "uriInfo", mockUriInfo(URI.create("http://localhost:8080/admin/org?tab=oauth")));

        Response response = api.orgPage("", "", "oauth");

        assertEquals(403, response.getStatus());
    }

    @Test
    void uploadRibShouldRedirectAfterSave() throws Exception {
        AdminApi api = new AdminApi();
        RibService ribService = mock(RibService.class);
        inject(api, "ribService", ribService);
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create("http://localhost:8080/admin/organisations/1?success=ribUploaded&tab=files#files")));

        Organisation org = new Organisation();
        org.setId(1);
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        InputStream file = new ByteArrayInputStream("%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        Response response = api.uploadRib(file);

        verify(ribService).save(file);
        assertEquals(200, response.getStatus());
        assertEquals("/admin/organisations/1?success=ribUploaded&tab=files#files", readRedirect(response));
    }

    @Test
    void uploadRibShouldRedirectWhenFileIsNotPdf() throws Exception {
        AdminApi api = new AdminApi();
        RibService ribService = mock(RibService.class);
        doThrow(new jakarta.ws.rs.WebApplicationException("RIB file must be a PDF", 400))
                .when(ribService)
                .save(org.mockito.ArgumentMatchers.any());
        inject(api, "ribService", ribService);
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create("http://localhost:8080/admin/organisations/1?error=invalidRib&tab=files#files")));

        Organisation org = new Organisation();
        org.setId(1);
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        Response response = api.uploadRib(new ByteArrayInputStream("hello".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(200, response.getStatus());
        assertEquals("/admin/organisations/1?error=invalidRib&tab=files#files", readRedirect(response));
    }

    @Test
    void uploadRibShouldRedirectOnIoFailure() throws Exception {
        AdminApi api = new AdminApi();
        RibService ribService = mock(RibService.class);
        doThrow(new IOException("disk")).when(ribService).save(org.mockito.ArgumentMatchers.any());
        inject(api, "ribService", ribService);
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create(
                        "http://localhost:8080/admin/organisations/1?error=ribUploadFailed&tab=files#files")));

        Organisation org = new Organisation();
        org.setId(1);
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        Response response = api.uploadRib(new ByteArrayInputStream("%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(200, response.getStatus());
        assertEquals("/admin/organisations/1?error=ribUploadFailed&tab=files#files", readRedirect(response));
    }

    @Test
    void saveConfigShouldPersistOauthOnAdminOrg() throws Exception {
        AdminApi api = new AdminApi();
        Config config = mock(Config.class);
        inject(api, "config", config);
        inject(api, "user", mockUser(true));
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create("/admin/org?success=configSaved&tab=oauth#oauth")));

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "oauth");
        form.putSingle("oauth.clientId", "cid");
        form.putSingle("oauth.authorizationUrl", "https://example.org/auth");

        Response response = api.saveConfig(form);

        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/admin/org?success=configSaved&tab=oauth#oauth"), response.getLocation());
        verify(config).updateProperties(argThat(updates ->
                "cid".equals(updates.get("oauth.clientId"))
                        && "https://example.org/auth".equals(updates.get("oauth.authorizationUrl"))));
    }

    @Test
    void deleteLogoShouldRedirect() throws Exception {
        AdminApi api = new AdminApi();
        LogoService logoService = mock(LogoService.class);
        Config config = mock(Config.class);
        inject(api, "logoService", logoService);
        inject(api, "config", config);
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create("http://localhost:8080/admin/organisations/1?success=logoDeleted&tab=files#files")));

        Organisation org = new Organisation();
        org.setId(1);
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        Response response = api.deleteLogo();

        verify(logoService).delete();
        verify(config).applyRuntime();
        assertEquals(200, response.getStatus());
        assertEquals("/admin/organisations/1?success=logoDeleted&tab=files#files", readRedirect(response));
    }

    @Test
    void deleteSignatureShouldRedirect() throws Exception {
        AdminApi api = new AdminApi();
        SignatureService signatureService = mock(SignatureService.class);
        inject(api, "signatureService", signatureService);
        inject(
                api,
                "uriInfo",
                mockUriInfo(URI.create(
                        "http://localhost:8080/admin/organisations/1?success=signatureDeleted&tab=files#files")));

        Organisation org = new Organisation();
        org.setId(1);
        RequestContext.openForTest((Connection) null);
        RequestContext.require().setOrganisation(org);

        Response response = api.deleteSignature();

        verify(signatureService).delete();
        assertEquals(200, response.getStatus());
        assertEquals("/admin/organisations/1?success=signatureDeleted&tab=files#files", readRedirect(response));
    }

    private static LoggedUser mockUser(boolean platformAdmin) {
        LoggedUser user = mock(LoggedUser.class);
        when(user.isPlatformAdmin()).thenReturn(platformAdmin);
        return user;
    }

    private static Config configWithProps() {
        Config config = mock(Config.class);
        Page page = new Page();
        page.setClubRegisterEnabled(true);
        when(config.getOrgFormValues()).thenReturn(Map.of("title", "Event Test", "org.name", "Club"));
        when(config.getPage()).thenReturn(page);
        when(config.pageForRequest()).thenReturn(page);
        when(config.getPublicBaseUrl()).thenReturn("http://localhost:8080");
        when(config.platformProperty(anyString(), anyString())).thenReturn("");
        when(config.isSecretConfigured(anyString())).thenReturn(false);
        return config;
    }

    private static String readRedirect(Response response) {
        Object entity = response.getEntity();
        if (!(entity instanceof String json) || json.isBlank()) {
            return null;
        }
        String redirect = new JSONObject(json).optString("redirect", null);
        return redirect == null || redirect.isBlank() ? null : redirect;
    }

    private static UriInfo mockUriInfo(URI target) {
        UriInfo uriInfo = mock(UriInfo.class);
        UriBuilder uriBuilder = mock(UriBuilder.class);
        when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);
        when(uriBuilder.path(anyString())).thenReturn(uriBuilder);
        when(uriBuilder.queryParam(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(uriBuilder);
        when(uriBuilder.fragment(anyString())).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(target);
        return uriInfo;
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
