package com.github.gcolin.membership;

import com.github.gcolin.auth.RequireRole;
import com.github.gcolin.membership.License;
import com.github.gcolin.membership.LicensePrice;
import com.github.gcolin.membership.MembershipOptionAccessRule;
import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.membership.LicenseDao;
import com.github.gcolin.membership.LicensePriceDao;
import jakarta.inject.Inject;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.github.gcolin.platform.MustacheHtml;

@Path("license-admin")
@RequireRole(RoleCode.TRESORIER)
public class LicenseAdminApi {

    @Inject
    private LicenseDao licenseDao;

    @Inject
    private LicensePriceDao licensePriceDao;

    @Context
    private UriInfo uriInfo;

    @GET
    public MustacheHtml list() {
        Map<String, Object> model = new HashMap<>();
        model.put("licenses", licenseDao.all());
        return new MustacheHtml(model, "membership/licenseAdmin.mustache");
    }

    @GET
    @Path("new")
    public MustacheHtml newLicense() {
        License license = new License("");
        Map<String, Object> model = new HashMap<>();
        model.put("license", license);
        MembershipOptionAccessRule[] accessRules = MembershipOptionAccessRule.values();
        model.put("accessRules", accessRules);
        MembershipMustacheView.enrichLicenseEditPage(model, license, accessRules);
        return new MustacheHtml(model, "membership/licenseEdit.mustache");
    }

    @GET
    @Path("{id:\\d+}")
    public MustacheHtml edit(@PathParam("id") Integer id) {
        License license = licenseDao.find(id);
        List<LicensePrice> prices = licensePriceDao.all();
        Map<String, Object> model = new HashMap<>();
        model.put("license", license);
        model.put("allPrices", prices);
        MembershipOptionAccessRule[] accessRules = MembershipOptionAccessRule.values();
        model.put("accessRules", accessRules);
        MembershipMustacheView.enrichLicenseEditPage(model, license, accessRules);
        return new MustacheHtml(model, "membership/licenseEdit.mustache");
    }

    @POST
    public Response save(
            @FormParam("id") Integer id,
            @FormParam("name") String name,
            @FormParam("accessRule") String accessRuleStr,
            @FormParam("toRemove") String toRemove) {
        if ("true".equals(toRemove)) {
            // Delete license and its associated prices
            licensePriceDao.all().stream()
                    .filter(p -> p.getLicense().getId().equals(id))
                    .forEach(licensePriceDao::remove);
            licenseDao.remove(id);
            return Response.seeOther(
                            uriInfo.getBaseUriBuilder().path("license-admin").build())
                    .build();
        }

        MembershipOptionAccessRule accessRule = MembershipOptionAccessRule.ALL;
        if (accessRuleStr != null && !accessRuleStr.isBlank()) {
            try {
                accessRule = MembershipOptionAccessRule.valueOf(accessRuleStr);
            } catch (IllegalArgumentException e) {
                // Use default ALL
            }
        }

        License license;
        if (id != null && id > 0) {
            license = licenseDao.find(id);
            license.setName(name);
            license.setAccessRule(accessRule);
            licenseDao.merge(license);
        } else {
            license = new License(name);
            license.setAccessRule(accessRule);
            licenseDao.persist(license);
        }

        return Response.seeOther(
                        uriInfo.getBaseUriBuilder().path("license-admin").build())
                .build();
    }

    @GET
    @Path("{licenseId:\\d+}/price")
    public MustacheHtml listPrices(@PathParam("licenseId") Integer licenseId) {
        License license = licenseDao.find(licenseId);
        List<LicensePrice> prices = licensePriceDao.all();
        Map<String, Object> model = new HashMap<>();
        model.put("license", license);
        model.put("prices", prices.stream()
                .filter(p -> p.getLicense().getId().equals(licenseId))
                .toList());
        return new MustacheHtml(model, "membership/licensePriceAdmin.mustache");
    }

    @GET
    @Path("{licenseId:\\d+}/price/new")
    public MustacheHtml newPrice(@PathParam("licenseId") Integer licenseId) {
        License license = licenseDao.find(licenseId);
        LicensePrice price = new LicensePrice();
        price.setLicense(license);
        Map<String, Object> model = new HashMap<>();
        model.put("price", price);
        model.put("license", license);
        MembershipMustacheView.enrichLicensePriceEditPage(model, license, price);
        return new MustacheHtml(model, "membership/licensePriceEdit.mustache");
    }

    @GET
    @Path("{licenseId:\\d+}/price/{priceId:\\d+}/edit")
    public MustacheHtml editPrice(@PathParam("licenseId") Integer licenseId, @PathParam("priceId") Integer priceId) {
        LicensePrice price = licensePriceDao.find(priceId);
        License license = price.getLicense();
        Map<String, Object> model = new HashMap<>();
        model.put("price", price);
        model.put("license", license);
        MembershipMustacheView.enrichLicensePriceEditPage(model, license, price);
        return new MustacheHtml(model, "membership/licensePriceEdit.mustache");
    }

    @POST
    @Path("price/save")
    public Response savePrice(
            @FormParam("id") Integer id,
            @FormParam("licenseId") Integer licenseId,
            @FormParam("category") String category,
            @FormParam("priceCents") String priceEuros,
            @FormParam("toRemove") String toRemove) {
        if ("true".equals(toRemove)) {
            licensePriceDao.remove(id);
            License license = licenseDao.find(licenseId);
            return Response.seeOther(
                            uriInfo.getBaseUriBuilder()
                                    .path("license-admin/{licenseId}/price")
                                    .build(licenseId))
                    .build();
        }

        // Convert euros to cents (multiply by 100 and round)
        Integer priceCents = Math.round(Float.parseFloat(priceEuros) * 100);

        LicensePrice price;
        License license = licenseDao.find(licenseId);
        if (id != null && id > 0) {
            price = licensePriceDao.find(id);
            price.setCategory(category);
            price.setPriceCents(priceCents);
            licensePriceDao.merge(price);
        } else {
            price = new LicensePrice(category, priceCents, license);
            licensePriceDao.persist(price);
        }

        return Response.seeOther(
                        uriInfo.getBaseUriBuilder()
                                .path("license-admin/{licenseId}/price")
                                .build(licenseId))
                .build();
    }
}
