package com.github.gcolin.platform;

import com.github.gcolin.organisation.OrgScope;
import java.util.List;

/** SQL fragment for org-scoped tables (license, club_season, event, event_group, big_event). */
public final class OrgFilter {

    private OrgFilter() {}

    public static String sql(String alias) {
        int orgId = OrgScope.currentFilterOrgId();
        if (orgId == OrgScope.FILTER_DISABLED) {
            return "";
        }
        String col = alias == null || alias.isBlank() ? "org_id" : alias + ".org_id";
        return col + " = ?";
    }

    public static void addArgs(List<Object> args) {
        int orgId = OrgScope.currentFilterOrgId();
        if (orgId != OrgScope.FILTER_DISABLED) {
            args.add(orgId);
        }
    }

    public static boolean active() {
        return OrgScope.currentFilterOrgId() != OrgScope.FILTER_DISABLED;
    }
}
