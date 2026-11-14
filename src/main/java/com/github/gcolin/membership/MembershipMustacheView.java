package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.platform.SelectItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public final class MembershipMustacheView {

    private MembershipMustacheView() {}

    public static void enrichListPage(
            Map<String, Object> model,
            List<Membership> memberships,
            Map<String, List<MembershipOptionSubscription>> subscriptionsByMembership) {
        List<MembershipListRow> rows = new ArrayList<>();
        for (Membership mem : memberships) {
            rows.add(toListRow(mem, subscriptionsByMembership));
        }
        model.put("memberships", memberships);
        model.put("safeMemberships", rows);
        model.put("hasMemberships", !rows.isEmpty());
        model.put("newMembershipHref", newMembershipHref(model));
    }

    public static void enrichEditPage(
            Map<String, Object> model,
            Membership membership,
            MembershipStatus[] statuses,
            List<License> licenses,
            List<MembershipOptionSubscription> subscriptions,
            List<MembershipOption> availableOptions,
            Integer seasonId) {
        Integer effectiveSeasonId = seasonId != null
                ? seasonId
                : (membership.getSeason() != null ? membership.getSeason().getId() : null);
        String effectiveLicenseType = membership.getLicenseType() != null
                        && !membership.getLicenseType().isBlank()
                ? membership.getLicenseType()
                : Membership.DEFAULT_LICENSE_TYPE;

        model.put("effectiveSeasonId", effectiveSeasonId);
        model.put("effectiveLicenseType", effectiveLicenseType);
        model.put("statusOptions", buildStatusOptions(statuses, membership.getStatus()));
        model.put("licenseOptions", buildLicenseOptions(licenses, effectiveLicenseType));
        model.put("seasonOptions", seasonOptionsExcludingAll(model.get("seasons"), effectiveSeasonId));
        model.put("membershipOptionRows", buildSubscriptionRows(subscriptions));
        model.put("hasMembershipOptions", subscriptions != null && !subscriptions.isEmpty());
        model.put("availableOptionChoices", buildAvailableOptionChoices(availableOptions));
        model.put("showPaymentLink", membership.getPayment() != null && membership.getPayment().getId() != null);
        model.put(
                "paymentId",
                membership.getPayment() != null ? membership.getPayment().getId() : null);
        model.put(
                "showNewPaymentLink",
                membership.getStatus() == MembershipStatus.PAID
                        && (membership.getPayment() == null || membership.getPayment().getId() == null));
    }

    public static void enrichNewPage(
            Map<String, Object> model, MembershipStatus[] statuses, List<License> licenses, Integer seasonId) {
        model.put("effectiveSeasonId", seasonId);
        model.put("statusOptions", buildStatusOptions(statuses, null));
        model.put("licenseOptions", buildLicenseOptions(licenses, Membership.DEFAULT_LICENSE_TYPE));
        model.put("seasonOptions", seasonOptionsExcludingAll(model.get("seasons"), seasonId));
    }

    public static void enrichOptionListPage(Map<String, Object> model, List<MembershipOption> options) {
        List<MembershipOptionRow> rows = new ArrayList<>();
        for (MembershipOption opt : options) {
            rows.add(toOptionRow(opt));
        }
        model.put("safeOptions", rows);
        model.put("hasOptions", !rows.isEmpty());
        Object seasonId = model.get("seasonId");
        model.put(
                "seasonQuery",
                seasonId != null ? "?seasonId=" + seasonId : "");
    }

    public static void enrichLicenseEditPage(
            Map<String, Object> model, License license, MembershipOptionAccessRule[] accessRules) {
        model.put("licenseIsEdit", license != null && license.getId() != null);
        model.put("accessRuleChoices", buildAccessRuleChoices(accessRules, license != null ? license.getAccessRule() : null));
        model.put("titleKey", license != null && license.getId() != null ? "page.licenseEdit" : "page.licenseNew");
    }

    public static void enrichLicensePriceListPage(
            Map<String, Object> model, License license, List<LicensePrice> prices) {
        String licenseName = license != null && license.getName() != null ? license.getName() : "";
        Integer licenseId = license != null ? license.getId() : null;
        // Resolved with request locale in MustacheMessageBodyWriter (avoid Locale.getDefault encoding).
        model.put("headingKey", "heading.licensePrices");
        model.put("headingParam", licenseName);
        model.put("titleKey", "page.licensePrices");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (prices != null) {
            for (LicensePrice price : prices) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", price.getId());
                row.put("category", price.getCategory() != null ? price.getCategory() : "");
                row.put("priceEuros", formatEuros(price.getPriceCents()));
                row.put(
                        "licenseId",
                        price.getLicense() != null && price.getLicense().getId() != null
                                ? price.getLicense().getId()
                                : licenseId);
                rows.add(row);
            }
        }
        model.put("priceRows", rows);
    }

    public static void enrichLicensePriceEditPage(Map<String, Object> model, License license, LicensePrice price) {
        model.put("priceIsEdit", price != null && price.getId() != null);
        model.put(
                "priceEuros",
                price != null && price.getPriceCents() != null
                        ? String.format(Locale.US, "%.2f", price.getPriceCents() / 100.0)
                        : "");
        model.put("categoryValue", price != null && price.getCategory() != null ? price.getCategory() : "");
        model.put("titleKey", price != null && price.getId() != null ? "page.licensePriceEdit" : "page.licensePriceNew");
        if (license != null) {
            model.put("licenseName", license.getName() != null ? license.getName() : "");
        }
    }

    public static void enrichBulkMailPage(
            Map<String, Object> model,
            MembershipStatus[] statuses,
            String selectedStatus,
            List<Membership> recipients) {
        List<Map<String, Object>> statusChoices = new ArrayList<>();
        for (MembershipStatus st : statuses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", st.name());
            item.put("label", i18n("clubRegister.membershipStatus." + st));
            item.put("selected", selectedStatus != null && selectedStatus.equals(st.name()));
            statusChoices.add(item);
        }
        model.put("statusChoices", statusChoices);
        model.put("hasStatus", selectedStatus != null && !selectedStatus.isBlank());
        model.put("selectedStatus", selectedStatus != null ? selectedStatus : "");

        List<Map<String, Object>> recipientRows = new ArrayList<>();
        if (recipients != null) {
            for (Membership membership : recipients) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("email", membership.getUser());
                String first = membership.getFirstname() != null ? membership.getFirstname() : "";
                String last = membership.getLastname() != null ? membership.getLastname() : "";
                String displayName = (first + " " + last).trim();
                row.put("hasDisplayName", !displayName.isBlank());
                row.put("displayName", displayName);
                recipientRows.add(row);
            }
        }
        model.put("recipientRows", recipientRows);
        model.put("hasRecipients", !recipientRows.isEmpty());
        model.put("recipientCount", recipientRows.size());
        Object seasonId = model.get("seasonId");
        model.put("membershipListHref", seasonId != null ? "?seasonId=" + seasonId : "");
        model.put("titleKey", "page.membershipMail");
        model.put("selectId", "membership_mail_season");
        model.put("autoSubmit", true);
        model.put("inline", false);
        Object sent = model.get("sent");
        if (sent instanceof Number sentCount) {
            model.put("sentMessageText", i18n("mail.sent", sentCount));
        }
    }

    public static void enrichOptionFilesPage(
            Map<String, Object> model,
            List<MembershipOption> options,
            MembershipOption selectedOption,
            List<MembershipOptionFile> files,
            String error) {
        List<MembershipOption> safeOptions = options != null ? options : List.of();
        List<Map<String, Object>> optionChoices = new ArrayList<>();
        Integer selectedOptionId = selectedOption != null ? selectedOption.getId() : null;
        for (MembershipOption opt : safeOptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", opt.getId());
            item.put(
                    "label",
                    opt.getOptionValue() != null ? opt.getOptionValue() : String.valueOf(opt.getId()));
            item.put("selected", selectedOptionId != null && selectedOptionId.equals(opt.getId()));
            optionChoices.add(item);
        }
        model.put("optionChoices", optionChoices);
        model.put("hasOptions", !optionChoices.isEmpty());
        model.put("selectedOptionId", selectedOptionId);
        model.put("showOptionPanel", selectedOption != null && selectedOptionId != null);
        if (error != null && !error.isBlank()) {
            model.put("hasError", true);
            model.put("errorMessageText", i18n("membership.files.error." + error));
        }
        Object sent = model.get("sent");
        if (sent instanceof Number sentCount) {
            model.put("sentMessageText", i18n("mail.sent", sentCount));
        }
        model.put("selectId", "membership_files_season");
        model.put("autoSubmit", true);
        model.put("inline", true);

        List<MembershipOptionFile> safeFiles = files != null ? files : List.of();
        List<Map<String, Object>> fileRows = new ArrayList<>();
        for (MembershipOptionFile file : safeFiles) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", file.getId());
            row.put("originalName", file.getOriginalName());
            row.put("uploadedBy", file.getUploadedBy() != null ? file.getUploadedBy() : "");
            if (file.getSizeBytes() != null) {
                row.put("hasSize", true);
                row.put("sizeLabel", String.format(Locale.FRANCE, "%.1f", file.getSizeBytes() / 1024.0) + " Ko");
            }
            fileRows.add(row);
        }
        model.put("fileRows", fileRows);
        model.put("hasFiles", !fileRows.isEmpty());
        Object seasonId = model.get("seasonId");
        model.put(
                "selectedOptionIdJs",
                selectedOptionId != null ? String.valueOf(selectedOptionId) : "null");
        model.put("seasonQuery", seasonId != null ? "&seasonId=" + seasonId : "");
        model.put("titleKey", "page.membershipOptionFiles");
    }

    public static void enrichOptionFileMailPage(
            Map<String, Object> model,
            MembershipOptionFile file,
            MembershipOption option,
            List<MembershipOptionFileAdminApi.MailRecipient> recipients) {
        model.put(
                "optionLabel",
                option != null && option.getOptionValue() != null
                        ? option.getOptionValue()
                        : (option != null ? String.valueOf(option.getId()) : ""));
        model.put("defaultSubjectValue", model.get("defaultSubject") != null ? model.get("defaultSubject") : "");
        model.put("defaultBodyValue", model.get("defaultBody") != null ? model.get("defaultBody") : "");

        List<MembershipOptionFileAdminApi.MailRecipient> safe =
                recipients != null ? recipients : List.of();
        List<Map<String, Object>> recipientRows = new ArrayList<>();
        for (MembershipOptionFileAdminApi.MailRecipient recipient : safe) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("email", recipient.email());
            String displayName = recipient.displayName();
            row.put("hasDisplayName", displayName != null && !displayName.isBlank());
            row.put("displayName", displayName != null ? displayName : "");
            recipientRows.add(row);
        }
        model.put("recipientRows", recipientRows);
        model.put("hasRecipients", !recipientRows.isEmpty());
        model.put("recipientCount", recipientRows.size());
        Object seasonId = model.get("seasonId");
        model.put("filesBackHref", buildFilesBackHref(option, seasonId));
        model.put("titleKey", "page.membershipOptionFileMail");
        Object sent = model.get("sent");
        if (sent instanceof Number sentCount) {
            model.put("sentMessageText", i18n("mail.sent", sentCount));
        }
    }

    private static String buildFilesBackHref(MembershipOption option, Object seasonId) {
        if (option == null || option.getId() == null) {
            return "/membership-option-files";
        }
        String href = "/membership-option-files?optionId=" + option.getId();
        if (seasonId != null) {
            href += "&seasonId=" + seasonId;
        }
        return href;
    }

    public static void enrichOptionEditPage(
            Map<String, Object> model,
            MembershipOption option,
            MembershipOptionType[] optionTypes,
            MembershipOptionAccessRule[] accessRules,
            List<License> licenses,
            Integer seasonId) {
        Integer effectiveSeasonId = option.getSeason() != null && option.getSeason().getId() != null
                ? option.getSeason().getId()
                : seasonId;
        model.put("effectiveSeasonId", effectiveSeasonId);
        model.put("seasonOptions", seasonOptionsExcludingAll(model.get("seasons"), effectiveSeasonId));
        model.put("optionTypeChoices", buildOptionTypeChoices(optionTypes, option.getOptionType()));
        model.put("accessRuleChoices", buildAccessRuleChoices(accessRules, option.getAccessRule()));
        model.put("licenseChoices", buildLicenseChoices(licenses, option.getLicense()));
        model.put("optionIsEdit", option.getId() != null);
        model.put("amountCentsValue", option.getAmountCents() != null ? option.getAmountCents() : 0);
        Object sid = model.get("seasonId");
        model.put("seasonQuery", sid != null ? "?seasonId=" + sid : "");
    }

    private static MembershipListRow toListRow(
            Membership mem, Map<String, List<MembershipOptionSubscription>> subscriptionsByMembership) {
        MembershipListRow row = new MembershipListRow();
        row.setMembership(mem);
        String last = mem.getLastname() != null ? mem.getLastname() : "";
        String first = mem.getFirstname() != null ? mem.getFirstname() : "";
        row.setSearchName((last + " " + first).trim());
        row.setHasLicenseType(mem.getLicenseType() != null && !mem.getLicenseType().isBlank());
        if (row.isHasLicenseType()) {
            row.setLicenseTypeLabel(i18n("clubRegister.membershipOptions.license" + mem.getLicenseType()));
        }
        row.setSeasonName(
                mem.getSeason() != null && mem.getSeason().getName() != null ? mem.getSeason().getName() : "");
        row.setStatusLabel(
                mem.getStatus() != null ? i18n("clubRegister.membershipStatus." + mem.getStatus()) : "");
        row.setAmountFormatted(formatEuros(mem.getAmountCents()));
        List<String> optionValues = new ArrayList<>();
        if (mem.getId() != null && subscriptionsByMembership != null) {
            List<MembershipOptionSubscription> subs =
                    subscriptionsByMembership.get(String.valueOf(mem.getId()));
            if (subs != null) {
                for (MembershipOptionSubscription mos : subs) {
                    if (mos.getMembershipOption() != null
                            && mos.getMembershipOption().getOptionValue() != null) {
                        optionValues.add(mos.getMembershipOption().getOptionValue());
                    }
                }
            }
        }
        row.setOptionValues(optionValues);
        row.setHasOptions(!optionValues.isEmpty());
        return row;
    }

    private static MembershipOptionRow toOptionRow(MembershipOption opt) {
        MembershipOptionRow row = new MembershipOptionRow();
        row.setId(opt.getId());
        row.setOptionTypeName(opt.getOptionType() != null ? opt.getOptionType().name() : "");
        row.setSeasonName(
                opt.getSeason() != null && opt.getSeason().getName() != null ? opt.getSeason().getName() : "");
        row.setOptionValue(opt.getOptionValue() != null ? opt.getOptionValue() : "");
        row.setHasLicense(opt.getLicense() != null);
        if (row.isHasLicense()) {
            row.setLicenseLabel(i18n("label.license") + " " + opt.getLicense().getName());
        }
        row.setAmountFormatted(formatEuros(opt.getAmountCents()));
        row.setHasAccessRule(opt.getAccessRule() != null);
        if (row.isHasAccessRule()) {
            row.setAccessRuleLabel(i18n("membershipOptionAccessRule." + opt.getAccessRule()));
        }
        return row;
    }

    private static List<Map<String, Object>> buildStatusOptions(MembershipStatus[] statuses, MembershipStatus current) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (MembershipStatus status : statuses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", status.name());
            item.put("label", i18n("clubRegister.membershipStatus." + status));
            item.put("selected", current != null && current == status);
            options.add(item);
        }
        return options;
    }

    private static List<Map<String, Object>> buildLicenseOptions(List<License> licenses, String selectedName) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (License lic : licenses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", lic.getName());
            item.put("label", i18n("clubRegister.membershipOptions.license" + lic.getName()));
            item.put("selected", selectedName != null && selectedName.equals(lic.getName()));
            options.add(item);
        }
        return options;
    }

    private static List<Map<String, Object>> seasonOptionsExcludingAll(Object seasonsObj, Integer effectiveSeasonId) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (!(seasonsObj instanceof List<?> seasons)) {
            return options;
        }
        String effective = effectiveSeasonId != null ? String.valueOf(effectiveSeasonId) : null;
        for (Object seasonObj : seasons) {
            if (!(seasonObj instanceof SelectItem seasonItem)) {
                continue;
            }
            if (String.valueOf(ClubSeasonFilter.ALL_SEASONS_ID).equals(seasonItem.getValue())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", seasonItem.getValue());
            item.put("label", seasonItem.getLabel());
            item.put(
                    "selected",
                    effective != null && effective.equals(seasonItem.getValue()));
            options.add(item);
        }
        return options;
    }

    private static List<Map<String, Object>> buildSubscriptionRows(List<MembershipOptionSubscription> subscriptions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (subscriptions == null) {
            return rows;
        }
        for (MembershipOptionSubscription mos : subscriptions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", mos.getId());
            MembershipOption opt = mos.getMembershipOption();
            row.put(
                    "optionTypeName",
                    opt != null && opt.getOptionType() != null ? opt.getOptionType().name() : "");
            row.put(
                    "optionValue",
                    opt != null && opt.getOptionValue() != null ? opt.getOptionValue() : "");
            int cents = opt != null && opt.getAmountCents() != null ? opt.getAmountCents() : 0;
            row.put("amountFormatted", formatEuros(cents));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> buildAvailableOptionChoices(List<MembershipOption> availableOptions) {
        List<Map<String, Object>> choices = new ArrayList<>();
        if (availableOptions == null) {
            return choices;
        }
        for (MembershipOption availOpt : availableOptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", availOpt.getId());
            item.put("optionValue", availOpt.getOptionValue());
            item.put("amountFormatted", formatEuros(availOpt.getAmountCents()));
            choices.add(item);
        }
        return choices;
    }

    private static List<Map<String, Object>> buildOptionTypeChoices(
            MembershipOptionType[] optionTypes, MembershipOptionType current) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (MembershipOptionType optType : optionTypes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", optType.name());
            item.put("label", i18n("membershipOptionType." + optType));
            item.put("selected", current != null && current == optType);
            choices.add(item);
        }
        return choices;
    }

    private static List<Map<String, Object>> buildAccessRuleChoices(
            MembershipOptionAccessRule[] accessRules, MembershipOptionAccessRule current) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (MembershipOptionAccessRule accessRule : accessRules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", accessRule.name());
            item.put("label", i18n("membershipOptionAccessRule." + accessRule));
            item.put("selected", current != null && current == accessRule);
            choices.add(item);
        }
        return choices;
    }

    private static List<Map<String, Object>> buildLicenseChoices(List<License> licenses, License selected) {
        List<Map<String, Object>> choices = new ArrayList<>();
        for (License lic : licenses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", lic.getId());
            item.put("name", lic.getName());
            item.put(
                    "selected",
                    selected != null
                            && selected.getId() != null
                            && selected.getId().equals(lic.getId()));
            choices.add(item);
        }
        return choices;
    }

    private static String newMembershipHref(Map<String, Object> model) {
        Object seasonId = model.get("seasonId");
        return seasonId != null ? "?seasonId=" + seasonId : "";
    }

    private static String formatEuros(Integer cents) {
        int value = cents != null ? cents : 0;
        return String.format(Locale.US, "%.2f", value / 100.0);
    }

    private static String i18n(String key) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.getDefault());
        return bundle.containsKey(key) ? bundle.getString(key) : key;
    }

    private static String i18n(String key, Object... params) {
        String pattern = i18n(key);
        if (params == null || params.length == 0) {
            return pattern;
        }
        return new java.text.MessageFormat(pattern, Locale.getDefault()).format(params);
    }

    public static final class MembershipListRow {
        private Membership membership;
        private String searchName;
        private boolean hasLicenseType;
        private String licenseTypeLabel;
        private String seasonName;
        private String statusLabel;
        private String amountFormatted;
        private boolean hasOptions;
        private List<String> optionValues = List.of();

        public Membership getMembership() {
            return membership;
        }

        public void setMembership(Membership membership) {
            this.membership = membership;
        }

        public String getSearchName() {
            return searchName;
        }

        public void setSearchName(String searchName) {
            this.searchName = searchName;
        }

        public boolean isHasLicenseType() {
            return hasLicenseType;
        }

        public void setHasLicenseType(boolean hasLicenseType) {
            this.hasLicenseType = hasLicenseType;
        }

        public String getLicenseTypeLabel() {
            return licenseTypeLabel;
        }

        public void setLicenseTypeLabel(String licenseTypeLabel) {
            this.licenseTypeLabel = licenseTypeLabel;
        }

        public String getSeasonName() {
            return seasonName;
        }

        public void setSeasonName(String seasonName) {
            this.seasonName = seasonName;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public void setStatusLabel(String statusLabel) {
            this.statusLabel = statusLabel;
        }

        public String getAmountFormatted() {
            return amountFormatted;
        }

        public void setAmountFormatted(String amountFormatted) {
            this.amountFormatted = amountFormatted;
        }

        public boolean isHasOptions() {
            return hasOptions;
        }

        public void setHasOptions(boolean hasOptions) {
            this.hasOptions = hasOptions;
        }

        public List<String> getOptionValues() {
            return optionValues;
        }

        public void setOptionValues(List<String> optionValues) {
            this.optionValues = optionValues;
        }
    }

    public static final class MembershipOptionRow {
        private Integer id;
        private String optionTypeName;
        private String seasonName;
        private String optionValue;
        private boolean hasLicense;
        private String licenseLabel;
        private String amountFormatted;
        private boolean hasAccessRule;
        private String accessRuleLabel;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getOptionTypeName() {
            return optionTypeName;
        }

        public void setOptionTypeName(String optionTypeName) {
            this.optionTypeName = optionTypeName;
        }

        public String getSeasonName() {
            return seasonName;
        }

        public void setSeasonName(String seasonName) {
            this.seasonName = seasonName;
        }

        public String getOptionValue() {
            return optionValue;
        }

        public void setOptionValue(String optionValue) {
            this.optionValue = optionValue;
        }

        public boolean isHasLicense() {
            return hasLicense;
        }

        public void setHasLicense(boolean hasLicense) {
            this.hasLicense = hasLicense;
        }

        public String getLicenseLabel() {
            return licenseLabel;
        }

        public void setLicenseLabel(String licenseLabel) {
            this.licenseLabel = licenseLabel;
        }

        public String getAmountFormatted() {
            return amountFormatted;
        }

        public void setAmountFormatted(String amountFormatted) {
            this.amountFormatted = amountFormatted;
        }

        public boolean isHasAccessRule() {
            return hasAccessRule;
        }

        public void setHasAccessRule(boolean hasAccessRule) {
            this.hasAccessRule = hasAccessRule;
        }

        public String getAccessRuleLabel() {
            return accessRuleLabel;
        }

        public void setAccessRuleLabel(String accessRuleLabel) {
            this.accessRuleLabel = accessRuleLabel;
        }
    }
}
