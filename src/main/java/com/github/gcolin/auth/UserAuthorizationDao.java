package com.github.gcolin.auth;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.dbutils.handlers.ColumnListHandler;

public class UserAuthorizationDao extends AbstractDao<UserAuthorization> {

    public UserAuthorizationDao() {
        super(UserAuthorization.class, "user_authorization");
    }

    public List<UserAuthorization> allOrdered() {
        return select("SELECT * FROM user_authorization ORDER BY email, role, scope_type, scope_id");
    }

    public Set<RoleCode> findActiveGlobalRoles(String email) {
        if (email == null || email.isBlank()) {
            return Set.of();
        }
        List<String> names = Db.query(
                "SELECT role FROM user_authorization WHERE email = ? AND scope_type = ?"
                        + " AND active = TRUE AND (valid_until IS NULL OR valid_until > ?)",
                new ColumnListHandler<String>(),
                email.trim().toLowerCase(),
                AuthorizationScopeType.GLOBAL,
                LocalDateTime.now());
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<RoleCode> roles = new HashSet<>();
        for (String name : names) {
            roles.add(RoleCode.valueOf(name));
        }
        return EnumSet.copyOf(roles);
    }

    public UserAuthorization upsert(
            String email,
            RoleCode role,
            AuthorizationScopeType scopeType,
            Integer scopeId,
            LocalDateTime validUntil,
            String grantedBy) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        String normalizedGrantedBy = grantedBy == null ? null : grantedBy.trim().toLowerCase();

        AuthorizationScopeType normalizedScopeType = scopeType == null ? AuthorizationScopeType.GLOBAL : scopeType;
        Integer normalizedScopeId = normalizedScopeType == AuthorizationScopeType.GLOBAL ? null : scopeId;

        UserAuthorization existing = findByNaturalKey(normalizedEmail, role, normalizedScopeType, normalizedScopeId);
        if (existing == null) {
            existing = new UserAuthorization();
            existing.setEmail(normalizedEmail);
            existing.setRole(role);
            existing.setScopeType(normalizedScopeType);
            existing.setScopeId(normalizedScopeId);
        }

        existing.setActive(true);
        existing.setValidUntil(validUntil);
        existing.setGrantedBy(normalizedGrantedBy);

        if (existing.getId() == null) {
            persist(existing);
            return existing;
        }
        return merge(existing);
    }

    private UserAuthorization findByNaturalKey(
            String email, RoleCode role, AuthorizationScopeType scopeType, Integer scopeId) {
        if (scopeId == null) {
            return selectOne(
                    "SELECT * FROM user_authorization WHERE email = ? AND role = ? AND scope_type = ?"
                            + " AND scope_id IS NULL ORDER BY id LIMIT 1",
                    email,
                    role,
                    scopeType);
        }
        return selectOne(
                "SELECT * FROM user_authorization WHERE email = ? AND role = ? AND scope_type = ?"
                        + " AND scope_id = ? ORDER BY id LIMIT 1",
                email,
                role,
                scopeType,
                scopeId);
    }

    @Override
    public void persist(UserAuthorization authorization) {
        authorization.normalize();
        if (authorization.getId() == null) {
            int id = Db.insertReturningInt(
                    "INSERT INTO user_authorization (email, role, scope_type, scope_id, active, valid_until,"
                            + " granted_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    authorization.getEmail(),
                    Db.enumName(authorization.getRole()),
                    Db.enumName(authorization.getScopeType()),
                    authorization.getScopeId(),
                    authorization.isActive(),
                    authorization.getValidUntil(),
                    authorization.getGrantedBy());
            authorization.setId(id);
        } else {
            Db.update(
                    "UPDATE user_authorization SET email = ?, role = ?, scope_type = ?, scope_id = ?, active = ?,"
                            + " valid_until = ?, granted_by = ? WHERE id = ?",
                    authorization.getEmail(),
                    Db.enumName(authorization.getRole()),
                    Db.enumName(authorization.getScopeType()),
                    authorization.getScopeId(),
                    authorization.isActive(),
                    authorization.getValidUntil(),
                    authorization.getGrantedBy(),
                    authorization.getId());
        }
    }

    @Override
    public UserAuthorization merge(UserAuthorization authorization) {
        persist(authorization);
        return authorization;
    }
}
