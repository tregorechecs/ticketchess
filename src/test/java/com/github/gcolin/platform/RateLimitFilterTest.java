package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {
    @Test
    void skipsWithoutAnnotationOrForNonUnauthorizedResponse() throws Exception {
        RateLimitFilter filter = filterFor("unlimited", "remote");
        ContainerResponseContext response = response(200);
        filter.filter(mock(ContainerRequestContext.class), response);
        verify(response, never()).setStatus(anyInt());

        filter = new RateLimitFilter();
        inject(filter, "resourceInfo", null);
        response = response(401);
        filter.filter(mock(ContainerRequestContext.class), response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void recordsAttemptsAndReturns429OnSecondUnauthorizedAttempt() throws Exception {
        RateLimitFilter filter = filterFor("limited", "10.0.0.1");
        ContainerRequestContext request = request(null, null, "10.0.0.1");
        ContainerResponseContext response = response(401);
        filter.filter(request, response);
        verify(response, never()).setStatus(Response.Status.TOO_MANY_REQUESTS.getStatusCode());
        filter.filter(request, response);
        verify(response).setStatus(429);
        verify(response).setEntity("Too many failed attempts. Please try again later.");
    }

    @Test
    void prefersForwardedThenRealThenServletAndUnknownAddresses() throws Exception {
        assert429ForHeaders("1.1.1.1, 2.2.2.2", null, "remote");
        assert429ForHeaders(null, "2.2.2.2", "remote");
        assert429ForHeaders(null, null, "3.3.3.3");
        assert429ForHeaders(null, null, null);
    }

    private static void assert429ForHeaders(String forwarded, String real, String remote) throws Exception {
        RateLimitFilter filter = filterFor("limited", remote);
        ContainerRequestContext request = request(forwarded, real, remote);
        ContainerResponseContext response = response(401);
        filter.filter(request, response);
        filter.filter(request, response);
        verify(response).setStatus(429);
    }

    private static RateLimitFilter filterFor(String methodName, String remote) throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        ResourceInfo info = mock(ResourceInfo.class);
        when(info.getResourceMethod()).thenReturn(DummyResource.class.getDeclaredMethod(methodName));
        when(info.getResourceClass()).thenReturn((Class) DummyResource.class);
        inject(filter, "resourceInfo", info);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn(remote);
        inject(filter, "servletRequest", servlet);
        return filter;
    }

    private static ContainerRequestContext request(String forwarded, String real, String remote) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getHeaderString("X-Forwarded-For")).thenReturn(forwarded);
        when(request.getHeaderString("X-Real-IP")).thenReturn(real);
        return request;
    }

    private static ContainerResponseContext response(int status) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static class DummyResource {
        @RateLimit(maxAttempts = 2, windowSeconds = 60)
        public void limited() {}
        public void unlimited() {}
    }
}
