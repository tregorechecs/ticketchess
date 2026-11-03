package com.github.gcolin.organisation;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;

/** Resolves the organisation of a request from the path {@code /o/{slug}/...} or from the Host header. */
public class OrganisationResolver {

    private static final long TTL_MS = 5_000;
    private static final String SLUG_PREFIX = "/o/";

    public record Resolution(Organisation organisation, String pathPrefix, boolean notFound) {}

    private volatile List<Organisation> cached;
    private volatile long loadedAt;

    public void invalidate() {
        cached = null;
    }

    public Resolution resolve(HttpServletRequest request, OrganisationDao dao) {
        List<Organisation> organisations = organisations(dao);
        if (organisations.isEmpty()) {
            return new Resolution(null, null, false);
        }
        String path = pathWithinContext(request);
        if (path.startsWith(SLUG_PREFIX)) {
            int end = path.indexOf('/', SLUG_PREFIX.length());
            String slug = (end < 0 ? path.substring(SLUG_PREFIX.length()) : path.substring(SLUG_PREFIX.length(), end))
                    .toLowerCase(Locale.ROOT);
            Organisation bySlug = organisations.stream()
                    .filter(o -> slug.equals(o.getSlug()))
                    .findFirst()
                    .orElse(null);
            if (bySlug == null || !bySlug.isActive()) {
                return new Resolution(null, null, true);
            }
            return new Resolution(bySlug, SLUG_PREFIX + slug, false);
        }
        Organisation byHost = matchHost(organisations, requestHost(request));
        if (byHost != null) {
            return byHost.isActive() ? new Resolution(byHost, null, false) : new Resolution(null, null, true);
        }
        Organisation fallback = organisations.stream()
                .filter(Organisation::isActive)
                .findFirst()
                .orElse(null);
        return new Resolution(fallback, null, fallback == null);
    }

    static Organisation matchHost(List<Organisation> organisations, String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String wanted = host.trim().toLowerCase(Locale.ROOT);
        String wantedNoPort = stripPort(wanted);
        Organisation hostOnly = null;
        for (Organisation org : organisations) {
            String orgHost = OrgConfig.hostOf(org.getBaseUrl());
            if (orgHost == null) {
                continue;
            }
            if (orgHost.equals(wanted)) {
                return org;
            }
            if (hostOnly == null && stripPort(orgHost).equals(wantedNoPort)) {
                hostOnly = org;
            }
        }
        return hostOnly;
    }

    private List<Organisation> organisations(OrganisationDao dao) {
        List<Organisation> list = cached;
        if (list == null || System.currentTimeMillis() - loadedAt > TTL_MS) {
            list = dao.detachAll(dao.allOrdered());
            loadedAt = System.currentTimeMillis();
            cached = list;
        }
        return list;
    }

    private static String requestHost(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String host = request.getHeader("Host");
        return host != null && !host.isBlank() ? host : request.getServerName();
    }

    private static String pathWithinContext(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return "/";
        }
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }
}
