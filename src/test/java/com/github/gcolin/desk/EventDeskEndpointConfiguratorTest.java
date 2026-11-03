package com.github.gcolin.desk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.ActiveLoggedUsers;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Config;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventDeskEndpointConfiguratorTest {
    @AfterEach
    void clearContext() throws Exception {
        var method = AppContext.class.getDeclaredMethod("clearTestInstance");
        method.setAccessible(true);
        method.invoke(null);
    }

    @Test
    void handshakeAcceptsAuthorizedDeskTicketAndExtractsAllowedEvents() throws Exception {
        Config config = config();
        EventDeskService service = mock(EventDeskService.class);
        when(service.hasDeskAccess("desk@test")).thenReturn(true);
        install(config, service);
        String ticket = Jwts.builder().subject("desk@test").claim("scope", "desk")
                .claim("eventIds", List.of(12, "13", "bad")).signWith(config.getKeys(), Config.JWT_ALGORITHM).compact();
        HandshakeRequest request = request(URI.create("/ws/event/12/desk?ticket=" + ticket));
        when(request.getParameterMap()).thenReturn(Map.of("ticket", List.of(ticket)));
        ServerEndpointConfig sec = mock(ServerEndpointConfig.class);
        Map<String, Object> props = new java.util.HashMap<>();
        when(sec.getUserProperties()).thenReturn(props);
        new EventDeskEndpointConfigurator().modifyHandshake(sec, request, mock(HandshakeResponse.class));
        assertEquals("desk@test", props.get("email"));
        assertEquals(true, props.get("authorized"));
        assertEquals(List.of(12, 13), props.get("allowedEventIds"));
    }

    @Test
    void handshakeFallsBackToHttpSessionAndRejectsInvalidTicketOrEvent() throws Exception {
        Config config = config();
        EventDeskService service = mock(EventDeskService.class);
        when(service.hasDeskAccess("session@test")).thenReturn(true);
        install(config, service);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("auth.email")).thenReturn("session@test");
        HandshakeRequest request = request(URI.create("/ws/event/99/desk?ticket=invalid"));
        when(request.getHttpSession()).thenReturn(session);
        when(request.getParameterMap()).thenReturn(Map.of());
        ServerEndpointConfig sec = mock(ServerEndpointConfig.class);
        Map<String, Object> props = new java.util.HashMap<>();
        when(sec.getUserProperties()).thenReturn(props);
        new EventDeskEndpointConfigurator().modifyHandshake(sec, request, mock(HandshakeResponse.class));
        assertEquals("session@test", props.get("email"));
        assertEquals(true, props.get("authorized"));

        when(session.getAttribute("auth.email")).thenReturn(null);
        props.clear();
        new EventDeskEndpointConfigurator().modifyHandshake(sec, request, mock(HandshakeResponse.class));
        assertEquals(false, props.get("authorized"));
    }

    private static Config config() {
        Config config = new Config();
        config.getProperties().setProperty("testmode", "true");
        config.applyRuntime();
        return config;
    }

    private static void install(Config config, EventDeskService service) throws Exception {
        var method = AppContext.class.getDeclaredMethod("initForTest", ActiveLoggedUsers.class);
        method.setAccessible(true);
        method.invoke(null, mock(ActiveLoggedUsers.class));
        AppContext context = AppContext.get();
        set(context, "config", config);
        set(context, "eventDeskService", service);
    }

    private static HandshakeRequest request(URI uri) {
        HandshakeRequest request = mock(HandshakeRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeaders()).thenReturn(Map.of());
        return request;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
