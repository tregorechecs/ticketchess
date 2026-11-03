package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.platform.MustacheHtml;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharlySelectApiTest {
    @Test
    void pageRejectsInvalidCallback() throws Exception {
        SharlySelectApi api = new SharlySelectApi();
        SharlyCallbackAllowlist allowlist = mock(SharlyCallbackAllowlist.class);
        when(allowlist.isAllowed("bad")).thenReturn(false);
        inject(api, "sharlyCallbackAllowlist", allowlist);
        MustacheHtml html = api.page("bad");
        assertEquals("event/sharlySelect.mustache", html.getTemplate());
        assertEquals("invalidCallback", html.getModel().get("error"));
        assertEquals(List.of(), html.getModel().get("items"));
    }

    @Test
    void pageListsOpenItemsAndSelectionNotFoundRendersAgain() throws Exception {
        SharlySelectApi api = new SharlySelectApi();
        SharlyCallbackAllowlist allowlist = mock(SharlyCallbackAllowlist.class);
        SharlySelectService service = mock(SharlySelectService.class);
        when(allowlist.isAllowed("http://localhost:3000/callback")).thenReturn(true);
        when(service.listOpenItems()).thenReturn(List.of());
        when(service.resolveSelection("event", 1, 2)).thenReturn(null);
        inject(api, "sharlyCallbackAllowlist", allowlist);
        inject(api, "sharlySelectService", service);
        MustacheHtml page = api.page("http://localhost:3000/callback");
        assertEquals("http://localhost:3000/callback", page.getModel().get("callback"));
        Response result = api.select("http://localhost:3000/callback", "event", 1, 2);
        assertEquals(200, result.getStatus());
        assertEquals("event/sharlySelect.mustache", ((MustacheHtml) result.getEntity()).getTemplate());
    }

    @Test
    void invalidSelectionIsBadRequestAndValidSelectionRedirects() throws Exception {
        SharlySelectApi api = new SharlySelectApi();
        SharlyCallbackAllowlist allowlist = mock(SharlyCallbackAllowlist.class);
        SharlySelectService service = mock(SharlySelectService.class);
        SharlyImportTokenService tokens = mock(SharlyImportTokenService.class);
        LoggedUser user = mock(LoggedUser.class);
        SharlySelectItem item =
                new SharlySelectItem(SharlySelectItem.Kind.EVENT, null, 2, "chess-2", "Open", 1);
        when(allowlist.isAllowed("http://localhost/cb")).thenReturn(true);
        when(service.resolveSelection("event", null, 2)).thenReturn(item);
        when(tokens.mint("u@test", "chess-2", "Open", null, 2)).thenReturn("jwt");
        when(user.getEmail()).thenReturn("u@test");
        inject(api, "sharlyCallbackAllowlist", allowlist);
        inject(api, "sharlySelectService", service);
        inject(api, "sharlyImportTokenService", tokens);
        inject(api, "loggedUser", user);
        assertEquals(400, api.select("javascript:bad", "event", null, 2).getStatus());
        Response response = api.select("http://localhost/cb", "event", null, 2);
        assertEquals(303, response.getStatus());
        assertTrue(response.getLocation().toString().contains("jwt=jwt"));
        assertEquals(URI.create(response.getLocation().toString()), response.getLocation());
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
