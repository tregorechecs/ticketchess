package com.github.gcolin.auth;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.Transactional;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.github.gcolin.platform.MustacheHtml;

@Path("logas")
@RequireRole(RoleCode.ADMIN)
public class LogAsApi {

    @GET
    @Transactional
    public MustacheHtml page() {
        Set<String> emails = new TreeSet<>();
        emails.addAll(RequestContext.require().playerSubscriptionDao().findDistinctCreationUsers());
        emails.addAll(RequestContext.require().paymentDao().findDistinctUserEmails());

        Map<String, Object> model = new HashMap<>();
        model.put("users", new ArrayList<>(emails));
        model.put("admins", AppContext.get().config().getAdmins());
        return new MustacheHtml(model, "auth/logas.mustache");
    }
}
