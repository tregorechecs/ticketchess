package com.github.gcolin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequireRoleFilterTest {

    private static final RequireRole REQUIRE_ROLE = SampleResource.class.getAnnotation(RequireRole.class);

    @Test
    void filterShouldRedirectGetHtmlRequestsToAdminWhenNotLogged() throws Exception {
        EnforceRoleFilter filter = new EnforceRoleFilter(REQUIRE_ROLE);
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        UriBuilder uriBuilder = mock(UriBuilder.class);

        when(ctx.getMethod()).thenReturn("GET");
        when(ctx.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.TEXT_HTML_TYPE));
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn("membership");
        when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);
        when(uriBuilder.path("admin")).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(URI.create("http://localhost:8080/admin"));

        LoggedUser user = mock(LoggedUser.class);
        try (TestContext ignored = TestContext.open(user)) {
            when(user.getEmail()).thenReturn(null);
            filter.filter(ctx);
        }

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(303, captor.getValue().getStatus());
        assertEquals("/admin", captor.getValue().getLocation().toString());
    }

    @Test
    void filterShouldReturnUnauthorizedForPostWhenNotLogged() throws Exception {
        EnforceRoleFilter filter = new EnforceRoleFilter(REQUIRE_ROLE);
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);

        when(ctx.getMethod()).thenReturn("POST");

        LoggedUser user = mock(LoggedUser.class);
        try (TestContext ignored = TestContext.open(user)) {
            when(user.getEmail()).thenReturn(null);
            filter.filter(ctx);
        }

        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(ctx).abortWith(captor.capture());
        assertEquals(401, captor.getValue().getStatus());
    }

    @Test
    void featureRegistersFilterForAnnotatedClassAndMethodOnly() throws Exception {
        RequireRoleFeature feature = new RequireRoleFeature();

        FeatureContext annotated = mock(FeatureContext.class);
        feature.configure(resourceInfo(SampleResource.class, "page"), annotated);
        verify(annotated).register(any(EnforceRoleFilter.class));

        FeatureContext methodLevel = mock(FeatureContext.class);
        feature.configure(resourceInfo(PlainResource.class, "secured"), methodLevel);
        verify(methodLevel).register(any(EnforceRoleFilter.class));

        FeatureContext open = mock(FeatureContext.class);
        feature.configure(resourceInfo(PlainResource.class, "open"), open);
        verify(open, never()).register(any(Object.class));
    }

    private static ResourceInfo resourceInfo(Class<?> type, String methodName) throws Exception {
        ResourceInfo info = mock(ResourceInfo.class);
        Method method = type.getMethod(methodName);
        when(info.getResourceMethod()).thenReturn(method);
        when(info.getResourceClass()).thenReturn((Class) type);
        return info;
    }

    @RequireRole(RoleCode.TRESORIER)
    static class SampleResource {
        public void page() {}
    }

    static class PlainResource {
        @RequireRole(RoleCode.ADMIN)
        public void secured() {}

        public void open() {}
    }
}
