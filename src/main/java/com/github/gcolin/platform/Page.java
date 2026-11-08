package com.github.gcolin.platform;

public class Page {

    private String title;
    private String contactUrl;
    private boolean clubRegisterEnabled;
    private boolean membershipsEnabled = true;
    private boolean eventsEnabled = true;
    private boolean paymentsEnabled = true;
    private String orgName;
    private String orgEmail;
    private String orgAddress;
    private String orgHostingAddress;
    private String logoUrl;
    private String backgroundUrl;
    private String accountUrl;
    private String sourceUrl;
    private String primaryColor;
    private String secondaryColor;
    private String primaryRgb;
    private String secondaryRgb;
    private String primaryHover;
    private String secondaryHover;
    private String fontFamily;
    private boolean customFont;
    private String googleFontHref;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContactUrl() {
        return contactUrl;
    }

    public void setContactUrl(String contactUrl) {
        this.contactUrl = contactUrl;
    }

    public boolean isClubRegisterEnabled() {
        return clubRegisterEnabled;
    }

    public void setClubRegisterEnabled(boolean clubRegisterEnabled) {
        this.clubRegisterEnabled = clubRegisterEnabled;
    }

    public boolean isMembershipsEnabled() {
        return membershipsEnabled;
    }

    public void setMembershipsEnabled(boolean membershipsEnabled) {
        this.membershipsEnabled = membershipsEnabled;
    }

    public boolean isEventsEnabled() {
        return eventsEnabled;
    }

    public void setEventsEnabled(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }

    public boolean isPaymentsEnabled() {
        return paymentsEnabled;
    }

    public void setPaymentsEnabled(boolean paymentsEnabled) {
        this.paymentsEnabled = paymentsEnabled;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getOrgEmail() {
        return orgEmail;
    }

    public void setOrgEmail(String orgEmail) {
        this.orgEmail = orgEmail;
    }

    public String getOrgAddress() {
        return orgAddress;
    }

    public void setOrgAddress(String orgAddress) {
        this.orgAddress = orgAddress;
    }

    public String getOrgHostingAddress() {
        return orgHostingAddress;
    }

    public void setOrgHostingAddress(String orgHostingAddress) {
        this.orgHostingAddress = orgHostingAddress;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
    }

    public String getAccountUrl() {
        return accountUrl;
    }

    public void setAccountUrl(String accountUrl) {
        this.accountUrl = accountUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getPrimaryRgb() {
        return primaryRgb;
    }

    public void setPrimaryRgb(String primaryRgb) {
        this.primaryRgb = primaryRgb;
    }

    public String getSecondaryRgb() {
        return secondaryRgb;
    }

    public void setSecondaryRgb(String secondaryRgb) {
        this.secondaryRgb = secondaryRgb;
    }

    public String getPrimaryHover() {
        return primaryHover;
    }

    public void setPrimaryHover(String primaryHover) {
        this.primaryHover = primaryHover;
    }

    public String getSecondaryHover() {
        return secondaryHover;
    }

    public void setSecondaryHover(String secondaryHover) {
        this.secondaryHover = secondaryHover;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public boolean isCustomFont() {
        return customFont;
    }

    public void setCustomFont(boolean customFont) {
        this.customFont = customFont;
    }

    public String getGoogleFontHref() {
        return googleFontHref;
    }

    public void setGoogleFontHref(String googleFontHref) {
        this.googleFontHref = googleFontHref;
    }

    @Override
    public String toString() {
        return "Page [title=" + title + ", contactUrl=" + contactUrl + ", clubRegisterEnabled="
                + clubRegisterEnabled + ", membershipsEnabled=" + membershipsEnabled + ", eventsEnabled="
                + eventsEnabled + ", paymentsEnabled=" + paymentsEnabled + ", orgName=" + orgName + "]";
    }
}
