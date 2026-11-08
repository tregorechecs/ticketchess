package com.github.gcolin.platform;

import com.github.gcolin.organisation.OrganisationResolver;
import com.github.gcolin.organisation.OrgScope;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RequestContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        if (isReadOnlyMethod(httpRequest)) {
            RequestContext.openReadOnly(httpRequest);
        } else {
            RequestContext.open(httpRequest);
        }
        try {
            RequestContext ctx = RequestContext.require();
            OrganisationResolver.Resolution resolution =
                    AppContext.get().organisationResolver().resolve(httpRequest, ctx.organisationDao());
            if (resolution.notFound()) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (resolution.organisation() != null) {
                ctx.setOrganisation(resolution.organisation());
                OrgScope.enableFilter(resolution.organisation().getId());
            }
            chain.doFilter(httpRequest, response);
            RequestContext.commit();
        } catch (RuntimeException | IOException | ServletException e) {
            RequestContext.rollback();
            throw e;
        } finally {
            RequestContext.close();
        }
    }

    private static boolean isReadOnlyMethod(HttpServletRequest request) {
        String method = request.getMethod();
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
