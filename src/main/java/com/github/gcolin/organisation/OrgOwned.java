package com.github.gcolin.organisation;

/** Root entity owned by one organisation (column {@code org_id}). */
public interface OrgOwned {

    Integer getOrgId();

    void setOrgId(Integer orgId);
}
