package com.github.gcolin.organisation;

/** Assigns the current organisation to new {@link OrgOwned} entities. */
public final class OrgEntityListener {

    private OrgEntityListener() {}

    public static void assignOrg(Object entity) {
        if (entity instanceof OrgOwned owned && owned.getOrgId() == null) {
            owned.setOrgId(OrgScope.currentOrgIdOrDefault());
        }
    }
}
