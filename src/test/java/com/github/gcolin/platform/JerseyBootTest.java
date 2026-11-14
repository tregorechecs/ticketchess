package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import java.net.URI;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.junit.jupiter.api.Test;

/** Boots Jersey without HK2 (see {@link NonInjectionManagerFactory}) and serves a request in-process. */
class JerseyBootTest {

    @Test
    void jerseyApplicationHandlerStarts() {
        ApplicationHandler handler = new ApplicationHandler(new TicketChessApplication());
        assertNotNull(handler);
        assertEquals(
                ManualInjectionManager.class, handler.getInjectionManager().getClass());
    }

    @Test
    void getRootRedirectsToEvent() throws Exception {
        Page page = mock(Page.class);
        when(page.isEventsEnabled()).thenReturn(true);
        Config config = mock(Config.class);
        when(config.pageForRequest()).thenReturn(page);

        try (TestContext ignored = TestContext.open(
                app -> app.setConfig(config), ctx -> ctx.setLoggedUser(mock(LoggedUser.class)))) {
            ApplicationHandler handler = new ApplicationHandler(new TicketChessApplication());
            ContainerRequest request = new ContainerRequest(
                    URI.create("http://localhost:8080/"),
                    URI.create("http://localhost:8080/"),
                    "GET",
                    null,
                    new MapPropertiesDelegate(),
                    null);

            ContainerResponse response = handler.apply(request).get();

            assertEquals(303, response.getStatus());
            assertEquals(URI.create("http://localhost:8080/event"), response.getLocation());
        }
    }
}
