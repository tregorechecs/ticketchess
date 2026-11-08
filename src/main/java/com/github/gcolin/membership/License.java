package com.github.gcolin.membership;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.organisation.OrgOwned;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.platform.TimedEntity;

public class License extends TimedEntity implements OrgOwned {

    private Integer id;

    private Integer orgId;

    private String name;

    private MembershipOptionAccessRule accessRule = MembershipOptionAccessRule.ALL;

    public License() {}

    public License(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getOrgId() {
        return orgId;
    }

    @Override
    public void setOrgId(Integer orgId) {
        this.orgId = orgId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MembershipOptionAccessRule getAccessRule() {
        return accessRule;
    }

    public void setAccessRule(MembershipOptionAccessRule accessRule) {
        this.accessRule = accessRule;
    }

    @Override
    public String toString() {
        return "License{" + "id=" + id + ", orgId=" + orgId + ", name='" + name + '\'' + '}';
    }
}
