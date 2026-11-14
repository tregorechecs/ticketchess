package com.github.gcolin.auth;

import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;

/** Registers an {@link EnforceRoleFilter} on every resource method annotated (directly or via its class) with {@link RequireRole}. */
@Provider
public class RequireRoleFeature implements DynamicFeature {

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        RequireRole role = findRequiredRole(resourceInfo);
        if (role != null) {
            context.register(new EnforceRoleFilter(role));
        }
    }

    static RequireRole findRequiredRole(ResourceInfo resourceInfo) {
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            RequireRole methodAnnotation = method.getAnnotation(RequireRole.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }
        Class<?> resourceClass = resourceInfo.getResourceClass();
        return resourceClass == null ? null : resourceClass.getAnnotation(RequireRole.class);
    }
}
