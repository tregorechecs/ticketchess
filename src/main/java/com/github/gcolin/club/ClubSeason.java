package com.github.gcolin.club;

import com.github.gcolin.organisation.OrgOwned;
import com.github.gcolin.platform.TimedEntity;
import java.time.LocalDate;

public class ClubSeason extends TimedEntity implements OrgOwned {

    private Integer id;

    private Integer orgId;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

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
