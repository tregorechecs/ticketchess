package com.github.gcolin.organisation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "organisation",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_organisation_slug", columnNames = "slug"),
            @UniqueConstraint(name = "uk_organisation_base_url", columnNames = "base_url")
        })
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "super_user_email", nullable = false, length = 320)
    private String superUserEmail;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "stripe_public", length = 512)
    private String stripePublic;

    @Column(name = "stripe_secret", length = 512)
    private String stripeSecret;

    @Column(name = "stripe_keyprefix", length = 100)
    private String stripeKeyprefix;

    @Column(name = "stripe_simuled", nullable = false)
    private boolean stripeSimuled;

    @Column(name = "stripe_card_events", nullable = false)
    private boolean stripeCardEvents = true;

    @Column(name = "stripe_card_memberships", nullable = false)
    private boolean stripeCardMemberships = true;

    @Column(name = "payment_transfer_events", nullable = false)
    private boolean paymentTransferEvents = true;

    @Column(name = "payment_transfer_memberships", nullable = false)
    private boolean paymentTransferMemberships = true;

    @Column(name = "oauth_client_id", length = 512)
    private String oauthClientId;

    @Column(name = "oauth_client_secret", length = 512)
    private String oauthClientSecret;

    @Column(name = "oauth_authorization_url", length = 1000)
    private String oauthAuthorizationUrl;

    @Column(name = "oauth_token_url", length = 1000)
    private String oauthTokenUrl;

    @Column(name = "oauth_scope", length = 500)
    private String oauthScope;

    @Column(name = "oauth_logout_url", length = 1000)
    private String oauthLogoutUrl;

    @Column(name = "oauth_account_url", length = 1000)
    private String oauthAccountUrl;

    @Column(name = "oauth_userinfo_url", length = 1000)
    private String oauthUserinfoUrl;

    @Column(name = "mail_user_name", length = 320)
    private String mailUserName;

    @Column(name = "mail_password", length = 512)
    private String mailPassword;

    @Column(name = "settings_json", columnDefinition = "text")
    private String settingsJson;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
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
        return "Organisation{id=" + id + ", slug='" + slug + "', baseUrl='" + baseUrl + "'}";
    }
}
