package com.github.gcolin.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.platform.SelectItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MembershipMustacheViewTest {

    @Test
    void enrichListAndEditPagesBuildRowsAndChoices() {
        Membership membership = new Membership();
        membership.setId(7);
        membership.setFirstname("Ada");
        membership.setLastname("Lovelace");
        membership.setLicenseType("A");
        membership.setStatus(MembershipStatus.PAID);
        membership.setAmountCents(4250);
        ClubSeason season = new ClubSeason();
        season.setId(3);
        season.setName("2026");
        membership.setSeason(season);

        MembershipOption option = new MembershipOption();
        option.setId(4);
        option.setOptionValue("Cours");
        option.setAmountCents(1200);
        MembershipOptionSubscription subscription = new MembershipOptionSubscription();
        subscription.setId(9);
        subscription.setMembershipOption(option);

        Map<String, Object> model = new HashMap<>();
        model.put("seasonId", 3);
        SelectItem all = new SelectItem();
        all.setValue(String.valueOf(com.github.gcolin.club.ClubSeasonFilter.ALL_SEASONS_ID));
        SelectItem selected = new SelectItem();
        selected.setValue("3");
        selected.setLabel("2026");
        model.put("seasons", List.of(all, selected));

        MembershipMustacheView.enrichListPage(
                model, List.of(membership), Map.of("7", List.of(subscription)));
        assertTrue((Boolean) model.get("hasMemberships"));
        MembershipMustacheView.MembershipListRow row =
                ((List<MembershipMustacheView.MembershipListRow>) model.get("safeMemberships")).get(0);
        assertEquals("Lovelace Ada", row.getSearchName());
        assertEquals(List.of("Cours"), row.getOptionValues());
        assertTrue(row.isHasLicenseType());

        License license = new License();
        license.setName("A");
        MembershipMustacheView.enrichEditPage(
                model,
                membership,
                MembershipStatus.values(),
                List.of(license),
                List.of(subscription),
                List.of(option),
                null);
        assertEquals(3, model.get("effectiveSeasonId"));
        assertTrue((Boolean) model.get("showPaymentLink") == false);
        assertTrue((Boolean) model.get("showNewPaymentLink"));
        assertEquals(1, ((List<?>) model.get("membershipOptionRows")).size());
        assertEquals(1, ((List<?>) model.get("seasonOptions")).size());
    }

    @Test
    void enrichNewAndOptionPagesHandleEmptyAndSelectedValues() {
        Map<String, Object> model = new HashMap<>();
        License defaultLicense = new License();
        defaultLicense.setName(Membership.DEFAULT_LICENSE_TYPE);
        MembershipMustacheView.enrichNewPage(model, MembershipStatus.values(), List.of(defaultLicense), null);
        assertEquals(Membership.DEFAULT_LICENSE_TYPE, ((List<Map<?, ?>>) model.get("licenseOptions")).get(0).get("value"));
        assertTrue(((List<?>) model.get("seasonOptions")).isEmpty());

        MembershipOption option = new MembershipOption();
        option.setId(2);
        option.setOptionType(MembershipOptionType.COURSES);
        option.setOptionValue("Training");
        option.setAmountCents(500);
        option.setAccessRule(MembershipOptionAccessRule.ALL);
        Map<String, Object> optionModel = new HashMap<>();
        optionModel.put("seasonId", 4);
        MembershipMustacheView.enrichOptionListPage(optionModel, List.of(option));
        MembershipMustacheView.MembershipOptionRow row =
                ((List<MembershipMustacheView.MembershipOptionRow>) optionModel.get("safeOptions")).get(0);
        assertEquals("COURSES", row.getOptionTypeName());
        assertTrue(row.isHasAccessRule());
        assertEquals("?seasonId=4", optionModel.get("seasonQuery"));

        Map<String, Object> licenseModel = new HashMap<>();
        MembershipMustacheView.enrichLicenseEditPage(licenseModel, null, MembershipOptionAccessRule.values());
        assertFalse((Boolean) licenseModel.get("licenseIsEdit"));
        MembershipMustacheView.enrichLicensePriceEditPage(licenseModel, null, null);
        assertEquals("", licenseModel.get("priceEuros"));
    }
}
