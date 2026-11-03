package com.github.gcolin.membership;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.organisation.OrgOwned;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.platform.TimedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.eclipse.persistence.annotations.AdditionalCriteria;

@Entity
@Table(
        name = "license",
        uniqueConstraints = @UniqueConstraint(name = "uk_license_org_name", columnNames = {"org_id", "name"}))
@EntityListeners(OrgEntityListener.class)
@AdditionalCriteria(":" + OrgScope.ORG_ID_PROPERTY + " = " + OrgScope.FILTER_DISABLED
        + " or this.orgId = :" + OrgScope.ORG_ID_PROPERTY)
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class License extends TimedEntity implements OrgOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_rule", nullable = false)
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
