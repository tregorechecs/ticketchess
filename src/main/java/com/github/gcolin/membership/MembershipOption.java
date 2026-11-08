package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.platform.TimedEntity;

public class MembershipOption extends TimedEntity {

    private Integer id;

    private MembershipOptionType optionType;

    private String optionValue;

    private Integer amountCents;

    private MembershipOptionAccessRule accessRule = MembershipOptionAccessRule.ALL;

    private License license;

    private ClubSeason season;

    public MembershipOption() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public MembershipOptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(MembershipOptionType optionType) {
        this.optionType = optionType;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }

    public Integer getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Integer amountCents) {
        this.amountCents = amountCents;
    }

    public MembershipOptionAccessRule getAccessRule() {
        return accessRule;
    }

    public void setAccessRule(MembershipOptionAccessRule accessRule) {
        this.accessRule = accessRule;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    public ClubSeason getSeason() {
        return season;
    }

    public void setSeason(ClubSeason season) {
        this.season = season;
    }
}
