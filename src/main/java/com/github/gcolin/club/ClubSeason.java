package com.github.gcolin.club;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.organisation.OrgOwned;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.platform.TimedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.eclipse.persistence.annotations.AdditionalCriteria;

@Entity
@Table(name = "club_season")
@EntityListeners(OrgEntityListener.class)
@AdditionalCriteria(":" + OrgScope.ORG_ID_PROPERTY + " = " + OrgScope.FILTER_DISABLED
        + " or this.orgId = :" + OrgScope.ORG_ID_PROPERTY)
public class ClubSeason extends TimedEntity implements OrgOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false, columnDefinition = "boolean not null default false")
    private boolean current;

    public ClubSeason() {}

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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }
}
