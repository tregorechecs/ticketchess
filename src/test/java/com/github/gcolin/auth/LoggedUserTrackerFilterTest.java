package com.github.gcolin.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.TestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

class LoggedUserTrackerFilterTest {

    @Test
    void filterShouldTouchWhenLoggedAndSessionExists() throws Exception {
        LoggedUserTrackerFilter filter = new LoggedUserTrackerFilter();
        LoggedUser user = mock(LoggedUser.class);
        ActiveLoggedUsers active = mock(ActiveLoggedUsers.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);

        when(user.isLogged()).thenReturn(true);
        when(user.getEmail()).thenReturn("u@test.com");
        when(user.getUsername()).thenReturn("user");
        when(request.getSession(false)).thenReturn(session);
        when(session.getId()).thenReturn("sid");

        try (TestContext ignored = TestContext.open(user, active)) {
            RequestContext.require().setRequest(request);
            filter.filter(mock(ContainerRequestContext.class));
        }

        verify(active).touch("sid", "u@test.com", "user", false);
    }

    @Test
    void filterShouldDoNothingWhenNotLogged() throws Exception {
        LoggedUserTrackerFilter filter = new LoggedUserTrackerFilter();
        LoggedUser user = mock(LoggedUser.class);
        ActiveLoggedUsers active = mock(ActiveLoggedUsers.class);

        when(user.isLogged()).thenReturn(false);

        try (TestContext ignored = TestContext.open(user, active)) {
            filter.filter(mock(ContainerRequestContext.class));
        }

        verify(active, never())
                .touch(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }
}
