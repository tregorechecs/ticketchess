package com.github.gcolin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.auth.ActiveLoggedUsers;
import com.github.gcolin.auth.ActiveSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;

class LoggedUsersApiTest {

    @Test
    void pageShouldListAllActiveSessions() throws Exception {
        LoggedUsersApi api = new LoggedUsersApi();
        ActiveLoggedUsers activeUsers = mock(ActiveLoggedUsers.class);

        ActiveSession session = mock(ActiveSession.class);
        when(session.getEmail()).thenReturn("user@test.com");
        when(session.getUsername()).thenReturn("testuser");
        when(session.isAdmin()).thenReturn(true);
        when(session.getLastSeenMillis()).thenReturn(System.currentTimeMillis());
        when(session.getSessionId()).thenReturn("session-123-abc-def");

        when(activeUsers.listActive()).thenReturn(List.of(session));

        try (TestContext tc = TestContext.openApp(app -> {
            app.setActiveLoggedUsers(activeUsers);
        })) {

            MustacheHtml html = api.page();

            assertEquals("auth/loggedusers.mustache", html.getTemplate());
            Map<String, Object> model = html.getModel();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) model.get("sessions");
            assertEquals(1, rows.size());
            assertEquals("user@test.com", rows.get(0).get("email"));
            assertEquals("testuser", rows.get(0).get("username"));
            assertEquals(true, rows.get(0).get("admin"));
            assertEquals(1, model.get("sessionCount"));
        }
    }

    @Test
    void pageWithMultipleSessionsShouldFormatAllCorrectly() throws Exception {
        LoggedUsersApi api = new LoggedUsersApi();
        ActiveLoggedUsers activeUsers = mock(ActiveLoggedUsers.class);

        ActiveSession session1 = mock(ActiveSession.class);
        when(session1.getEmail()).thenReturn("admin@test.com");
        when(session1.getUsername()).thenReturn("admin");
        when(session1.isAdmin()).thenReturn(true);
        when(session1.getLastSeenMillis()).thenReturn(System.currentTimeMillis());
        when(session1.getSessionId()).thenReturn("abc123def456");

        ActiveSession session2 = mock(ActiveSession.class);
        when(session2.getEmail()).thenReturn("user@test.com");
        when(session2.getUsername()).thenReturn("user");
        when(session2.isAdmin()).thenReturn(false);
        when(session2.getLastSeenMillis()).thenReturn(System.currentTimeMillis() - 60000);
        when(session2.getSessionId()).thenReturn("xyz");

        when(activeUsers.listActive()).thenReturn(List.of(session1, session2));

        try (TestContext tc = TestContext.openApp(app -> {
            app.setActiveLoggedUsers(activeUsers);
        })) {

            MustacheHtml html = api.page();

            Map<String, Object> model = html.getModel();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) model.get("sessions");
            assertEquals(2, rows.size());
            assertEquals(2, model.get("sessionCount"));
        }
    }
}
