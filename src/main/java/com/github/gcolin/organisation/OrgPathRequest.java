package com.github.gcolin.organisation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** Hides the {@code /o/{slug}} prefix from routing while keeping it in the context path used for links. */
public class OrgPathRequest extends HttpServletRequestWrapper {

    private final String prefix;
    private final String contextPath;

    public OrgPathRequest(HttpServletRequest request, String prefix) {
        super(request);
        this.prefix = prefix;
        this.contextPath = request.getContextPath() + prefix;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getServletPath() {
        String path = super.getServletPath();
        if (path != null && path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return path;
    }

    @Override
    public String getPathInfo() {
        String info = super.getPathInfo();
        if (info != null && info.startsWith(prefix)) {
            String rest = info.substring(prefix.length());
            return rest.isEmpty() ? null : rest;
        }
        return info;
    }
}
