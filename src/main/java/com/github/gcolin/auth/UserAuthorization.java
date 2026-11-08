package com.github.gcolin.auth;

import java.time.LocalDateTime;

public class UserAuthorization {

    private Integer id;

    private String email;

    private RoleCode role;

    private AuthorizationScopeType scopeType = AuthorizationScopeType.GLOBAL;

    private Integer scopeId;

    private boolean active = true;

    private LocalDateTime validUntil;

    private String grantedBy;

    public UserAuthorization() {}

    public void normalize() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (grantedBy != null) {
            grantedBy = grantedBy.trim().toLowerCase();
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public RoleCode getRole() {
        return role;
    }

    public void setRole(RoleCode role) {
        this.role = role;
    }

    public AuthorizationScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(AuthorizationScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Integer getScopeId() {
        return scopeId;
    }

    public void setScopeId(Integer scopeId) {
        this.scopeId = scopeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }
}
