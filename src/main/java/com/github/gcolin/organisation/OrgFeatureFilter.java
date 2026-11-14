package com.github.gcolin.organisation;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.Page;
import com.github.gcolin.platform.plugin.PluginRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Blocks HTTP access to feature modules disabled for the current organisation.
 * Must run after {@link com.github.gcolin.platform.RequestContextFilter}.
 */
public class OrgFeatureFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        AppContext app = AppContext.get();
        if (app == null || app.config() == null) {
            chain.doFilter(request, response);
            return;
        }
        Config config = app.config();
        Page page = config.pageForRequest();
        String path = relativePath(httpRequest);
        if (isBlocked(path, page, app.pluginRegistry(), config.enabledPlugins())) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri.startsWith("/") ? uri : "/" + uri;
    }

    /** Plugin ownership of the path when a registry is available, else the page feature flags. */
    static boolean isBlocked(String path, Page page, PluginRegistry registry, Set<String> enabledPlugins) {
        if (registry == null) {
            return isBlocked(path, page);
        }
        return !registry.isPathAllowed(path, enabledPlugins) || isStatisticsBlocked(path, page);
    }

    private static boolean isStatisticsBlocked(String path, Page page) {
        return page != null
                && path != null
                && !page.isEventsEnabled()
                && !page.isPaymentsEnabled()
                && matchesAny(path, "/statistics");
    }

    static boolean isBlocked(String path, Page page) {
        if (page == null || path == null) {
            return false;
        }
        if (!page.isEventsEnabled()
                && matchesAny(
                        path,
                        "/event",
                        "/events",
                        "/myevents",
                        "/eventcollection",
                        "/eventgroup",
                        "/admin/events",
                        "/playersubscription",
                        "/dashboard")) {
            return true;
        }
        if (!page.isMembershipsEnabled()
                && matchesAny(
                        path,
                        "/club-register",
                        "/membership",
                        "/membership-option",
                        "/license-admin",
                        "/license")) {
            return true;
        }
        if (!page.isPaymentsEnabled() && matchesAny(path, "/payment")) {
            return true;
        }
        return isStatisticsBlocked(path, page);
    }

    private static boolean matchesAny(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (pathEqualsOrPrefix(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathEqualsOrPrefix(String path, String prefix) {
        if (path.equals(prefix)) {
            return true;
        }
        if (path.length() <= prefix.length()) {
            return false;
        }
        if (!path.startsWith(prefix)) {
            return false;
        }
        char next = path.charAt(prefix.length());
        return next == '/' || next == '-' || next == '?';
    }
}
