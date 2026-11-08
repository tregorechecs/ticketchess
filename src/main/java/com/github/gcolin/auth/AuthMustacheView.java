package com.github.gcolin.auth;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public final class AuthMustacheView {

    private static final DateTimeFormatter VALID_UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private AuthMustacheView() {}

    public static void enrichLoggedUsers(Map<String, Object> model, List<Map<String, Object>> sessions) {
        List<Map<String, Object>> rows = sessions != null ? sessions : List.of();
        model.put("safeSessions", rows);
        model.put("hasSessions", !rows.isEmpty());
        model.put("sessionCount", rows.size());
    }

    public static void enrichUserAuthorization(
            Map<String, Object> model,
            List<UserAuthorization> authorizations,
            List<RoleCode> roles) {
        List<UserAuthorization> safe = authorizations != null ? authorizations : List.of();
        List<Map<String, Object>> authRows = new ArrayList<>();
        for (UserAuthorization authorization : safe) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", authorization.getId());
            row.put("email", authorization.getEmail());
            row.put("roleLabel", i18n("role." + authorization.getRole()));
            if (authorization.getValidUntil() != null) {
                row.put("hasValidUntil", true);
                row.put("validUntilFormatted", VALID_UNTIL_FORMAT.format(authorization.getValidUntil()));
            } else {
                row.put("hasValidUntil", false);
            }
            authRows.add(row);
        }
        model.put("safeAuthorizations", authRows);
        model.put("hasAuthorizations", !authRows.isEmpty());
        model.put("authorizationCount", authRows.size());

        List<Map<String, Object>> roleOptions = new ArrayList<>();
        if (roles != null) {
            for (RoleCode roleOption : roles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", roleOption.name());
                item.put("label", i18n("role." + roleOption));
                roleOptions.add(item);
            }
        }
        model.put("roleOptions", roleOptions);
    }

    private static String i18n(String key) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.getDefault());
        return bundle.getString(key);
    }
}
