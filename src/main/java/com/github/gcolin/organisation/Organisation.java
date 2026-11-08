package com.github.gcolin.organisation;


public class Organisation {

    private Integer id;

    private String baseUrl;

    private String name;

    private String superUserEmail;

    private boolean active = true;

    private String stripePublic;

    private String stripeSecret;

    private String stripeKeyprefix;

    private boolean stripeSimuled;

    private boolean stripeCardEvents = true;

    private boolean stripeCardMemberships = true;

    private boolean paymentTransferEvents = true;

    private boolean paymentTransferMemberships = true;

    private String oauthClientId;

    private String oauthClientSecret;

    private String oauthAuthorizationUrl;

    private String oauthTokenUrl;

    private String oauthScope;

    private String oauthLogoutUrl;

    private String oauthAccountUrl;

    private String oauthUserinfoUrl;

    private String mailUserName;

    private String mailPassword;

    private String settingsJson;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSuperUserEmail() {
        return superUserEmail;
    }

    public void setSuperUserEmail(String superUserEmail) {
        this.superUserEmail = superUserEmail;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getStripePublic() {
        return stripePublic;
    }

    public void setStripePublic(String stripePublic) {
        this.stripePublic = stripePublic;
    }

    public String getStripeSecret() {
        return stripeSecret;
    }

    public void setStripeSecret(String stripeSecret) {
        this.stripeSecret = stripeSecret;
    }

    public String getStripeKeyprefix() {
        return stripeKeyprefix;
    }

    public void setStripeKeyprefix(String stripeKeyprefix) {
        this.stripeKeyprefix = stripeKeyprefix;
    }

    public boolean isStripeSimuled() {
        return stripeSimuled;
    }

    public void setStripeSimuled(boolean stripeSimuled) {
        this.stripeSimuled = stripeSimuled;
    }

    public boolean isStripeCardEvents() {
        return stripeCardEvents;
    }

    public void setStripeCardEvents(boolean stripeCardEvents) {
        this.stripeCardEvents = stripeCardEvents;
    }

    public boolean isStripeCardMemberships() {
        return stripeCardMemberships;
    }

    public void setStripeCardMemberships(boolean stripeCardMemberships) {
        this.stripeCardMemberships = stripeCardMemberships;
    }

    public boolean isPaymentTransferEvents() {
        return paymentTransferEvents;
    }

    public void setPaymentTransferEvents(boolean paymentTransferEvents) {
        this.paymentTransferEvents = paymentTransferEvents;
    }

    public boolean isPaymentTransferMemberships() {
        return paymentTransferMemberships;
    }

    public void setPaymentTransferMemberships(boolean paymentTransferMemberships) {
        this.paymentTransferMemberships = paymentTransferMemberships;
    }

    public String getOauthClientId() {
        return oauthClientId;
    }

    public void setOauthClientId(String oauthClientId) {
        this.oauthClientId = oauthClientId;
    }

    public String getOauthClientSecret() {
        return oauthClientSecret;
    }

    public void setOauthClientSecret(String oauthClientSecret) {
        this.oauthClientSecret = oauthClientSecret;
    }

    public String getOauthAuthorizationUrl() {
        return oauthAuthorizationUrl;
    }

    public void setOauthAuthorizationUrl(String oauthAuthorizationUrl) {
        this.oauthAuthorizationUrl = oauthAuthorizationUrl;
    }

    public String getOauthTokenUrl() {
        return oauthTokenUrl;
    }

    public void setOauthTokenUrl(String oauthTokenUrl) {
        this.oauthTokenUrl = oauthTokenUrl;
    }

    public String getOauthScope() {
        return oauthScope;
    }

    public void setOauthScope(String oauthScope) {
        this.oauthScope = oauthScope;
    }

    public String getOauthLogoutUrl() {
        return oauthLogoutUrl;
    }

    public void setOauthLogoutUrl(String oauthLogoutUrl) {
        this.oauthLogoutUrl = oauthLogoutUrl;
    }

    public String getOauthAccountUrl() {
        return oauthAccountUrl;
    }

    public void setOauthAccountUrl(String oauthAccountUrl) {
        this.oauthAccountUrl = oauthAccountUrl;
    }

    public String getOauthUserinfoUrl() {
        return oauthUserinfoUrl;
    }

    public void setOauthUserinfoUrl(String oauthUserinfoUrl) {
        this.oauthUserinfoUrl = oauthUserinfoUrl;
    }

    public String getMailUserName() {
        return mailUserName;
    }

    public void setMailUserName(String mailUserName) {
        this.mailUserName = mailUserName;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson;
    }

    @Override
    public String toString() {
        return "Organisation{id=" + id + ", baseUrl='" + baseUrl + "'}";
    }
}
