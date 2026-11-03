package com.github.gcolin.organisation;

import com.github.gcolin.platform.RequestContext;
import jakarta.persistence.EntityManager;

public final class OrgScope {

    /**
     * EclipseLink {@code @AdditionalCriteria} parameter. Must always be set: EclipseLink rejects a
     * missing property even when the criteria allows {@code null}. Use {@link #FILTER_DISABLED} to
     * see all organisations.
     */
    public static final String ORG_ID_PROPERTY = "orgId";

    public static final int DEFAULT_ORG_ID = 1;

    /** Sentinel for {@link #disableFilter(EntityManager)} — must match AdditionalCriteria. */
    public static final int FILTER_DISABLED = -1;

    private OrgScope() {}

    public static Organisation currentOrg() {
        RequestContext ctx = RequestContext.current();
        return ctx == null ? null : ctx.organisation();
    }

    public static Integer currentOrgId() {
        RequestContext ctx = RequestContext.current();
        return ctx == null ? null : ctx.orgId();
    }

    public static int currentOrgIdOrDefault() {
        Integer orgId = currentOrgId();
        return orgId == null ? DEFAULT_ORG_ID : orgId;
    }

    public static void enableFilter(EntityManager em, int orgId) {
        em.setProperty(ORG_ID_PROPERTY, orgId);
    }

    public static void disableFilter(EntityManager em) {
        em.setProperty(ORG_ID_PROPERTY, FILTER_DISABLED);
    }
}
