package com.github.gcolin.membership;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.club.ClubSeasonDao;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.platform.MustacheHtml;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequireRole(value = RoleCode.TRESORIER, or = RoleCode.ENTRAINEUR)
@Path("membership-option-admin")
public class MembershipOptionAdminApi {

    @Inject
    private MembershipOptionDao membershipOptionDao;

    @Inject
    private LicenseDao licenseDao;

    @Inject
    private ClubSeasonFilter clubSeasonFilter;

    @Inject
    private ClubSeasonDao clubSeasonDao;

    @Context
    UriInfo uriInfo;

    @GET
    public MustacheHtml list(@QueryParam("seasonId") Integer seasonId) {
        SeasonScope scope = clubSeasonFilter.resolve(seasonId);
        List<MembershipOption> options = membershipOptionDao.all(scope);
        Map<String, Object> model = new HashMap<>();
        model.put("options", options);
        clubSeasonFilter.addToModel(model, seasonId);
        MembershipMustacheView.enrichOptionListPage(model, options);
        return new MustacheHtml(model, "membership/membershipOption.mustache");
    }

    @GET
    @Path("new")
    public MustacheHtml createPage(@QueryParam("seasonId") Integer seasonId) {
        Map<String, Object> model = new HashMap<>();
        MembershipOption option = new MembershipOption();
        model.put("option", option);
        model.put("optionTypes", MembershipOptionType.values());
        model.put("accessRules", MembershipOptionAccessRule.values());
        model.put("licenses", licenseDao.all());
        model.put("seasonId", clubSeasonFilter.effectiveSeasonId(seasonId));
        clubSeasonFilter.addToModel(model, seasonId);
        MembershipMustacheView.enrichOptionEditPage(
                model,
                option,
                MembershipOptionType.values(),
                MembershipOptionAccessRule.values(),
                licenseDao.all(),
                clubSeasonFilter.effectiveSeasonId(seasonId));
        return new MustacheHtml(model, "membership/membershipOptionEdit.mustache");
    }

    @GET
    @Path("{id}/edit")
    public MustacheHtml editPage(@PathParam("id") Integer id, @QueryParam("seasonId") Integer seasonId) {
        MembershipOption option = membershipOptionDao.find(id);
        if (option == null) {
            return new MustacheHtml(Map.of(
                    "statusCode", 404,
                    "statusMessage", "Not Found",
                    "errors", List.of()), "platform/error.mustache");
        }
        Map<String, Object> model = new HashMap<>();
        model.put("option", option);
        model.put("optionTypes", MembershipOptionType.values());
        model.put("accessRules", MembershipOptionAccessRule.values());
        model.put("licenses", licenseDao.all());
        model.put("seasonId", clubSeasonFilter.effectiveSeasonId(seasonId));
        clubSeasonFilter.addToModel(model, seasonId);
        MembershipMustacheView.enrichOptionEditPage(
                model,
                option,
                MembershipOptionType.values(),
                MembershipOptionAccessRule.values(),
                licenseDao.all(),
                clubSeasonFilter.effectiveSeasonId(seasonId));
        return new MustacheHtml(model, "membership/membershipOptionEdit.mustache");
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(
            @FormParam("optionType") String optionType,
            @FormParam("optionValue") String optionValue,
            @FormParam("amountCents") Integer amountCents,
            @FormParam("accessRule") String accessRule,
            @FormParam("licenseId") Integer licenseId,
            @FormParam("seasonId") Integer seasonId) {
        MembershipOption option = new MembershipOption();
        option.setOptionType(MembershipOptionType.valueOf(optionType));
        option.setOptionValue(optionValue);
        option.setAmountCents(amountCents);
        option.setAccessRule(parseAccessRule(accessRule));
        if (licenseId != null && licenseId > 0) {
            option.setLicense(licenseDao.find(licenseId));
        }
        option.setSeason(resolveSeason(seasonId));
        membershipOptionDao.persist(option);
        return Response.seeOther(listUri(seasonId)).build();
    }

    @POST
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response update(
            @PathParam("id") Integer id,
            @FormParam("optionType") String optionType,
            @FormParam("optionValue") String optionValue,
            @FormParam("amountCents") Integer amountCents,
            @FormParam("accessRule") String accessRule,
            @FormParam("licenseId") Integer licenseId,
            @FormParam("seasonId") Integer seasonId) {
        MembershipOption option = membershipOptionDao.find(id);
        if (option == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        option.setOptionType(MembershipOptionType.valueOf(optionType));
        option.setOptionValue(optionValue);
        option.setAmountCents(amountCents);
        option.setAccessRule(parseAccessRule(accessRule));
        if (licenseId != null && licenseId > 0) {
            option.setLicense(licenseDao.find(licenseId));
        } else {
            option.setLicense(null);
        }
        option.setSeason(resolveSeason(seasonId));
        membershipOptionDao.merge(option);
        return Response.seeOther(listUri(seasonId)).build();
    }

    @POST
    @Path("{id}/delete")
    public Response delete(@PathParam("id") Integer id, @FormParam("seasonId") Integer seasonId) {
        membershipOptionDao.remove(id);
        return Response.seeOther(listUri(seasonId)).build();
    }

    private URI listUri(Integer seasonId) {
        UriBuilder builder = uriInfo.getBaseUriBuilder().path("membership-option-admin");
        if (seasonId != null) {
            builder.queryParam("seasonId", seasonId);
        }
        return builder.build();
    }

    private MembershipOptionAccessRule parseAccessRule(String accessRule) {
        if (accessRule == null || accessRule.isBlank()) {
            return MembershipOptionAccessRule.ALL;
        }
        return MembershipOptionAccessRule.valueOf(accessRule);
    }

    private ClubSeason resolveSeason(Integer seasonId) {
        Integer effectiveId = clubSeasonFilter.effectiveSeasonId(seasonId);
        if (effectiveId == null) {
            return null;
        }
        return clubSeasonDao.find(effectiveId);
    }
}
