package com.github.gcolin.organisation;

import com.github.gcolin.platform.RequestContext;

public final class OrgScope {

    public static final int DEFAULT_ORG_ID = 1;

    /** Sentinel to see all organisations (replaces EclipseLink AdditionalCriteria disable). */
    public static final int FILTER_DISABLED = -1;

    private static final ThreadLocal<Integer> FILTER_ORG_ID = ThreadLocal.withInitial(() -> FILTER_DISABLED);

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

    public static int currentFilterOrgId() {
        Integer value = FILTER_ORG_ID.get();
        return value == null ? FILTER_DISABLED : value;
    }

    public static void enableFilter(int orgId) {
        FILTER_ORG_ID.set(orgId);
    }

    public static void disableFilter() {
        FILTER_ORG_ID.set(FILTER_DISABLED);
    }

    public static void clearFilter() {
        FILTER_ORG_ID.remove();
    }
}
