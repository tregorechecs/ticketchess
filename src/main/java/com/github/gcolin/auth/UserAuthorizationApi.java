package com.github.gcolin.auth;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.auth.AuthorizationScopeType;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.auth.UserAuthorizationDao;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.gcolin.platform.MustacheHtml;

@Path("user-authorization")
@RequireRole(RoleCode.ADMIN)
public class UserAuthorizationApi {

    @GET
    public MustacheHtml page() {
        Map<String, Object> model = new HashMap<>();
        List<UserAuthorization> authorizations = RequestContext.require().userAuthorizationDao().allOrdered();
        List<RoleCode> roles = Arrays.asList(RoleCode.values());
        model.put("authorizations", authorizations);
        model.put("roles", roles);
        model.put("scopeTypes", Arrays.asList(AuthorizationScopeType.values()));
        AuthMustacheView.enrichUserAuthorization(model, authorizations, roles);
        model.put("titleKey", "page.userAuthorizations");
        model.put("activePageTitleKey", "page.userAuthorizations");
        return new MustacheHtml(model, "auth/userauthorization.mustache");
    }

    @POST
    public Response save(
            @FormParam("toRemove") String toRemove,
            @FormParam("id") Integer id,
            @FormParam("email") String email,
            @FormParam("role") String role,
            @FormParam("scopeType") String scopeType,
            @FormParam("scopeId") String scopeId,
            @FormParam("validUntil") String validUntil) {
        if ("true".equals(toRemove)) {
            if (id != null) {
                RequestContext.require().userAuthorizationDao().remove(id);
                AppContext.get().caches().getRoleCache().invalidateAll();
            }
            return redirectToPage();
        }

        RoleCode roleCode = RoleCode.valueOf(role);
        AuthorizationScopeType authorizationScopeType = AuthorizationScopeType.valueOf(scopeType);
        Integer parsedScopeId = parseNullableInteger(scopeId);

        if (authorizationScopeType != AuthorizationScopeType.GLOBAL && parsedScopeId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("scopeId is required when scopeType is not GLOBAL")
                    .build();
        }

        RequestContext.require().userAuthorizationDao().upsert(
                email,
                roleCode,
                authorizationScopeType,
                parsedScopeId,
                parseNullableDateTime(validUntil),
                RequestContext.require().loggedUser() == null ? null : RequestContext.require().loggedUser().getEmail());
        AppContext.get().caches().getRoleCache().invalidateAll();

        return redirectToPage();
    }

    private Response redirectToPage() {
        return Response.seeOther(
                        RequestContext.require().uriInfo().getBaseUriBuilder().path("user-authorization").build())
                .build();
    }

    private Integer parseNullableInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private LocalDateTime parseNullableDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
