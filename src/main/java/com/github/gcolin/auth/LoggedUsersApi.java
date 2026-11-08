package com.github.gcolin.auth;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.auth.ActiveLoggedUsers;
import com.github.gcolin.auth.ActiveSession;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.gcolin.platform.MustacheHtml;

@Path("logged-users")
@RequireRole(RoleCode.ADMIN)
public class LoggedUsersApi {

    private static final DateTimeFormatter LAST_SEEN_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Inject
    private ActiveLoggedUsers activeLoggedUsers;

    @GET
    public MustacheHtml page() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActiveSession session : activeLoggedUsers.listActive()) {
            Map<String, Object> row = new HashMap<>();
            row.put("email", session.getEmail());
            row.put("username", session.getUsername());
            row.put("admin", session.isAdmin());
            row.put("lastSeen", LAST_SEEN_FORMAT.format(Instant.ofEpochMilli(session.getLastSeenMillis())));
            row.put(
                    "sessionIdShort",
                    session.getSessionId().length() > 8
                            ? session.getSessionId().substring(0, 8) + "…"
                            : session.getSessionId());
            rows.add(row);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("sessions", rows);
        AuthMustacheView.enrichLoggedUsers(model, rows);
        model.put("titleKey", "page.loggedUsers");
        model.put("activePageTitleKey", "page.loggedUsers");
        return new MustacheHtml(model, "auth/loggedusers.mustache");
    }
}
