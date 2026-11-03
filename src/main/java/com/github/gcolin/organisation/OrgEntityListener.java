package com.github.gcolin.organisation;

import jakarta.persistence.PrePersist;

/** Assigns the current organisation to new {@link OrgOwned} entities. */
public class OrgEntityListener {

    @PrePersist
    public void assignOrg(Object entity) {
        if (entity instanceof OrgOwned owned && owned.getOrgId() == null) {
            owned.setOrgId(OrgScope.currentOrgIdOrDefault());
        }
    }
}
