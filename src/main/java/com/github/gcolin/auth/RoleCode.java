package com.github.gcolin.auth;

import java.util.Set;

public enum RoleCode {
    ADMIN,
    TRESORIER,
    ENTRAINEUR,
    ARBITRE;

    public static boolean satisfies(Set<RoleCode> granted, RoleCode required) {
        if (required == null || granted == null || granted.isEmpty()) {
            return false;
        }
        if (granted.contains(ADMIN)) {
            return true;
        }
        return granted.contains(required);
    }
}
