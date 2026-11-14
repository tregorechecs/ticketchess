package com.github.gcolin.platform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;

public abstract class ApiTestBase {

    protected ApiTestBase() {}

    protected static void inject(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + target.getClass().getName());
    }

    protected static UriInfo mockUriInfo(String baseUri) {
        return mockUriInfo(URI.create(baseUri));
    }

    protected static UriInfo mockUriInfo(URI target) {
        UriInfo uriInfo = mock(UriInfo.class);
        UriBuilder uriBuilder = mock(UriBuilder.class);

        when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);
        when(uriBuilder.path(anyString())).thenReturn(uriBuilder);
        when(uriBuilder.queryParam(anyString(), any())).thenReturn(uriBuilder);
        when(uriBuilder.replaceQuery(anyString())).thenReturn(uriBuilder);
        when(uriBuilder.replaceQueryParam(anyString(), any())).thenReturn(uriBuilder);
        when(uriBuilder.fragment(anyString())).thenReturn(uriBuilder);
        when(uriBuilder.build()).thenReturn(target);
        when(uriBuilder.build(any())).thenReturn(target);

        return uriInfo;
    }

    protected static ClubSeasonFilter mockClubSeasonFilter() {
        ClubSeasonFilter filter = mock(ClubSeasonFilter.class);
        when(filter.resolve(any())).thenReturn(SeasonScope.all());
        doAnswer(invocation -> {
            Map<String, Object> model = invocation.getArgument(0);
            model.put("seasons", List.of());
            model.put("seasonId", invocation.getArgument(1));
            return null;
        }).when(filter).addToModel(any(), any());
        return filter;
    }
}
