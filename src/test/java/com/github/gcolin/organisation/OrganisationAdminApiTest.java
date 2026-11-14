package com.github.gcolin.organisation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.payment.RibService;
import com.github.gcolin.platform.BackgroundService;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.LogoService;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.SignatureService;
import com.github.gcolin.platform.TestContext;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrganisationAdminApiTest {

    private final OrganisationDao dao = mock(OrganisationDao.class);
    private final Config config = mock(Config.class);
    private final RibService rib = mock(RibService.class);
    private final LogoService logo = mock(LogoService.class);
    private final SignatureService signature = mock(SignatureService.class);
    private final BackgroundService background = mock(BackgroundService.class);
    private TestContext tc;

    @AfterEach
    void closeContext() {
        if (tc != null) {
            tc.close();
            tc = null;
        }
    }

    @Test
    void platformAdminRequirementAndPagesCoverAccessAndNotFound() throws Exception {
        OrganisationAdminApi api = newApi(false);
        assertEquals(403, api.createPage().getStatus());

        RequestContext.require().setLoggedUser(user(true, false));
        when(dao.allOrdered()).thenReturn(List.of());
        assertEquals(200, api.createPage().getStatus());
        assertEquals(404, api.editPage(9, "", "", "").getStatus());

        Organisation org = org(9);
        when(dao.find(9)).thenReturn(org);
        RequestContext.require().setLoggedUser(user(false, false));
        assertEquals(403, api.editPage(9, "", "", "").getStatus());
    }

    @Test
    void createValidatesIdentityAndDuplicateFields() throws Exception {
        OrganisationAdminApi api = newApi(true);

        assertEquals(200, api.create("not-a-url", "Club", "a@b.test").getStatus());
        when(dao.findByBaseUrl("http://club.test")).thenReturn(org(3));
        assertEquals(200, api.create("http://club.test", "Club", "a@b.test").getStatus());
    }

    @Test
    void updateHandlesMissingAndValidation() throws Exception {
        OrganisationAdminApi api = newApi(true);
        assertEquals(404, api.update(1, "http://x.test", "X", "x@y.test").getStatus());
    }

    @Test
    void saveConfigCoversInvalidSuccessAndIoFailure() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(4);
        when(dao.find(4)).thenReturn(org);
        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> invalid = new MultivaluedHashMap<>();
        invalid.putSingle("tab", "nope");
        assertEquals(303, api.saveConfig(4, invalid).getStatus());

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "identity");
        form.putSingle("title", " Club ");
        assertEquals(303, api.saveConfig(4, form).getStatus());
        verify(config).updateProperties(any());

        doThrow(new IOException("disk")).when(config).updateProperties(any());
        assertEquals(303, api.saveConfig(4, form).getStatus());
    }

    @Test
    void paymentTabKeysDropSellerFallbacksCoveredByIdentity() {
        Set<String> paymentKeys = OrganisationAdminApi.SETTINGS_TAB_KEYS.get("payment");
        assertFalse(paymentKeys.contains("invoice.seller.name"));
        assertFalse(paymentKeys.contains("invoice.seller.email"));
        assertFalse(paymentKeys.contains("invoice.seller.address1"));
        assertFalse(paymentKeys.contains("invoice.seller.website"));
        assertTrue(paymentKeys.contains("invoice.seller.phone"));
        assertTrue(paymentKeys.contains("invoice.seller.zip"));
        assertTrue(paymentKeys.contains("stripe.simuled"));
    }

    @Test
    void saveConfigFeaturesPersistsFeatureFlags() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(11);
        when(dao.find(11)).thenReturn(org);
        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "features");
        form.putSingle("feature.events", "true");
        // memberships + payments unchecked → false; membership payment methods forced off
        assertEquals(303, api.saveConfig(11, form).getStatus());
        verify(config).updateProperties(argThat(updates ->
                "true".equals(updates.get(OrgConfig.FEATURE_EVENTS))
                        && "false".equals(updates.get(OrgConfig.FEATURE_MEMBERSHIPS))
                        && "false".equals(updates.get(OrgConfig.FEATURE_PAYMENTS))
                        && "false".equals(updates.get("stripe.card.memberships"))
                        && "false".equals(updates.get("payment.transfer.memberships"))
                        && !updates.containsKey("stripe.card.events")
                        && !updates.containsKey("payment.transfer.events")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveConfigFeaturesPluginFormPersistsPluginListAndSyncsFeatureFlags() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(12);
        when(dao.find(12)).thenReturn(org);
        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "features");
        form.putSingle("pluginsForm", "true");
        form.putSingle("plugin.events", "true");
        form.putSingle("plugin.email", "true");
        assertEquals(303, api.saveConfig(12, form).getStatus());
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(config).updateProperties(captor.capture());
        Map<String, String> updates = captor.getValue();
        assertEquals("email,events", updates.get(OrgConfig.KEY_PLUGINS));
        assertEquals("true", updates.get(OrgConfig.FEATURE_EVENTS));
        assertEquals("false", updates.get(OrgConfig.FEATURE_MEMBERSHIPS));
        assertEquals("false", updates.get(OrgConfig.FEATURE_PAYMENTS));
        assertEquals("false", updates.get("stripe.card.memberships"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveConfigFeaturesLegacyFormKeepsStoredPluginListInSync() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(13);
        new OrgConfig(org).setPlugins(List.of("events", "membership", "payments", "email"));
        when(dao.find(13)).thenReturn(org);
        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "features");
        form.putSingle("feature.events", "true");
        assertEquals(303, api.saveConfig(13, form).getStatus());
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(config).updateProperties(captor.capture());
        assertEquals("events,email", captor.getValue().get(OrgConfig.KEY_PLUGINS));
    }

    @Test
    void editPageListsEveryPluginWithItsState() throws Exception {
        OrganisationAdminApi api = newApi(true);
        Organisation org = org(14);
        new OrgConfig(org).setPlugins(List.of("events", "desk"));
        when(dao.find(14)).thenReturn(org);
        when(config.pageForRequest()).thenReturn(new com.github.gcolin.platform.Page());
        RequestContext.require().setOrganisation(org);

        Response response = api.editPage(14, "features", "", "");
        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>)
                ((com.github.gcolin.platform.MustacheHtml) response.getEntity()).getModel().get("pluginOptions");
        assertEquals(7, options.size());
        for (Map<String, Object> option : options) {
            boolean expected = "events".equals(option.get("id")) || "desk".equals(option.get("id"));
            assertEquals(expected, option.get("checked"), String.valueOf(option.get("id")));
            assertTrue(String.valueOf(option.get("labelKey")).startsWith("plugin."));
        }
    }

    @Test
    void editPageRedirectsOauthTabToAdminOrg() throws Exception {
        OrganisationAdminApi api = newApi(true);
        Organisation org = org(9);
        when(dao.find(9)).thenReturn(org);
        RequestContext.require().setUriInfo(mockUriInfo(URI.create("/admin/org?tab=oauth#oauth")));

        Response response = api.editPage(9, "oauth", "", "");
        assertEquals(303, response.getStatus());
        assertEquals(URI.create("/admin/org?tab=oauth#oauth"), response.getLocation());
    }

    @Test
    void saveConfigRejectsOauthTabMovedToAdminOrg() throws Exception {
        OrganisationAdminApi api = newApi(true);
        Organisation org = org(10);
        when(dao.find(10)).thenReturn(org);
        RequestContext.require().setUriInfo(mockUriInfo(URI.create("/admin/organisations/10?error=configInvalid&tab=features#features")));
        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "oauth");
        form.putSingle("oauth.clientId", "cid");
        Response response = api.saveConfig(10, form);
        assertEquals(303, response.getStatus());
        assertEquals(
                URI.create("/admin/organisations/10?error=configInvalid&tab=features#features"),
                response.getLocation());
    }

    @Test
    void saveConfigIdentityAsPlatformAdminUpdatesIdentityFields() throws Exception {
        OrganisationAdminApi api = newApi(true);
        Organisation org = org(7);
        org.setActive(false);
        when(dao.find(7)).thenReturn(org);

        RequestContext.require().setOrganisation(org);

        MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tab", "identity");
        form.putSingle("title", "App");
        form.putSingle("org.name", "Club Seven");
        form.putSingle("base_url", "https://club7.example.org");
        form.putSingle("super_user_email", "admin7@example.org");

        assertEquals(303, api.saveConfig(7, form).getStatus());
        assertEquals("Club Seven", org.getName());
        assertEquals("https://club7.example.org", org.getBaseUrl());
        assertEquals("admin7@example.org", org.getSuperUserEmail());
        assertFalse(org.isActive());
        verify(dao).merge(org);
        verify(config).updateProperties(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveConfigPaymentSimuledDoesNotWipeKeysAndBlankPublicDisablesCards() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(8);
        when(dao.find(8)).thenReturn(org);
        when(rib.exists()).thenReturn(true);
        RequestContext.require().setOrganisation(org);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);

        MultivaluedMap<String, String> simuled = new MultivaluedHashMap<>();
        simuled.putSingle("tab", "payment");
        simuled.putSingle("stripe.simuled", "true");
        simuled.putSingle("stripe.card.events", "true");
        assertEquals(303, api.saveConfig(8, simuled).getStatus());
        verify(config).updateProperties(captor.capture());
        Map<String, String> simuledUpdates = captor.getValue();
        assertFalse(simuledUpdates.containsKey("stripe.public"));
        assertFalse(simuledUpdates.containsKey("stripe.secret"));
        assertFalse(simuledUpdates.containsKey("stripe.keyprefix"));
        assertEquals("true", simuledUpdates.get("stripe.simuled"));

        MultivaluedMap<String, String> noPublic = new MultivaluedHashMap<>();
        noPublic.putSingle("tab", "payment");
        noPublic.putSingle("stripe.public", "  ");
        noPublic.putSingle("stripe.card.events", "true");
        noPublic.putSingle("stripe.card.memberships", "true");
        assertEquals(303, api.saveConfig(8, noPublic).getStatus());
        verify(config, times(2)).updateProperties(captor.capture());
        Map<String, String> cardUpdates = captor.getValue();
        assertEquals("false", cardUpdates.get("stripe.card.events"));
        assertEquals("false", cardUpdates.get("stripe.card.memberships"));
    }

    @Test
    void fileEndpointsCoverSuccessErrorsAndNotFound() throws Exception {
        OrganisationAdminApi api = newApi(false);
        Organisation org = org(5);
        when(dao.find(5)).thenReturn(org);
        RequestContext.require().setUriInfo(uriInfo());
        RequestContext.require().setOrganisation(org);


        assertEquals(200, api.uploadLogo(5, new ByteArrayInputStream(new byte[0])).getStatus());
        verify(logo).save(any());
        doThrow(new WebApplicationException("PNG invalid", 400)).when(signature).save(any());
        assertEquals(200, api.uploadSignature(5, new ByteArrayInputStream(new byte[0])).getStatus());
        doThrow(new IOException("disk")).when(background).save(any());
        assertEquals(200, api.uploadBackground(5, new ByteArrayInputStream(new byte[0])).getStatus());
        assertEquals(200, api.uploadRib(5, new ByteArrayInputStream(new byte[0])).getStatus());

        assertEquals(200, api.deleteLogo(5).getStatus());
        assertEquals(200, api.deleteSignature(5).getStatus());
        assertEquals(200, api.deleteBackground(5).getStatus());
        assertEquals(200, api.deleteRib(5).getStatus());
        verify(rib).delete();

        when(logo.exists()).thenReturn(false);
        assertEquals(404, api.downloadLogo(5).getStatus());
        when(dao.find(5)).thenReturn(null);
        assertEquals(404, api.downloadRib(5).getStatus());
    }

    @Test
    void organisationAdminDeniesOrgFilesForNonAdmin() throws Exception {
        OrganisationAdminApi api = newApi(false);
        when(dao.find(6)).thenReturn(org(6));
        assertEquals(403, api.deleteRib(6).getStatus());
        assertEquals(403, api.downloadRib(6).getStatus());
    }

    private OrganisationAdminApi newApi(boolean platform) throws Exception {
        OrganisationAdminApi api = new OrganisationAdminApi();
        LoggedUser user = user(platform, !platform);
        tc = TestContext.open(app -> {
            app.setConfig(config);
            app.setRibService(rib);
            app.setLogoService(logo);
            app.setSignatureService(signature);
            app.setBackgroundService(background);
            app.setOrganisationResolver(mock(OrganisationResolver.class));
        }, ctx -> {
            ctx.setOrganisationDao(dao);
            ctx.setLoggedUser(user);
        });
        RequestContext.require().setUriInfo(uriInfo());
        return api;
    }

    private static Organisation org(int id) {
        Organisation org = new Organisation();
        org.setId(id);
        org.setBaseUrl("http://club" + id + ".test");
        org.setName("Club");
        org.setSuperUserEmail("admin@test.com");
        return org;
    }

    private static LoggedUser user(boolean platform, boolean admin) {
        LoggedUser user = mock(LoggedUser.class);
        when(user.isPlatformAdmin()).thenReturn(platform);
        when(user.isAdmin()).thenReturn(admin);
        return user;
    }

    private static UriInfo uriInfo() {
        return mockUriInfo(URI.create("http://localhost/admin/organisations/1"));
    }

    private static UriInfo mockUriInfo(URI target) {
        UriInfo info = mock(UriInfo.class);
        UriBuilder builder = mock(UriBuilder.class);
        when(info.getBaseUriBuilder()).thenReturn(builder);
        when(builder.path(anyString())).thenReturn(builder);
        when(builder.queryParam(anyString(), any())).thenReturn(builder);
        when(builder.fragment(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(target);
        return info;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
