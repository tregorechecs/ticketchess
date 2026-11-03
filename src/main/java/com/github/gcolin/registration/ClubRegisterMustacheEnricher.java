package com.github.gcolin.registration;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.membership.Membership;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.membership.MembershipOption;
import com.github.gcolin.membership.MembershipOptionFile;
import com.github.gcolin.membership.MembershipOptionSubscription;
import com.github.gcolin.membership.MembershipStatus;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.platform.Messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClubRegisterMustacheEnricher {

    private ClubRegisterMustacheEnricher() {}

    public static void enrich(Map<String, Object> model, Messages messages, LoggedUser user) {
        boolean logged = user != null && user.getEmail() != null;
        String query = model.get("query") instanceof String s ? s : null;
        boolean hasSearchQuery = query != null;

        @SuppressWarnings("unchecked")
        List<Membership> memberships =
                model.get("memberships") instanceof List<?> list ? (List<Membership>) list : List.of();
        boolean hasMemberships = !memberships.isEmpty();

        model.put("hasSearchQuery", hasSearchQuery);
        if (hasSearchQuery) {
            @SuppressWarnings("unchecked")
            List<?> players = model.get("players") instanceof List<?> list ? list : null;
            model.put("hasPlayers", players != null && !players.isEmpty());
            model.put("showNoPlayersFound", players != null && players.isEmpty());
        }

        String success = model.get("success") instanceof String s ? s : null;
        boolean hideHelp = logged && hasMemberships && query == null;
        model.put("helpHiddenStyle", hideHelp ? "display:none" : "");
        model.put(
                "showNewInscriptionButton",
                logged && query == null && hasMemberships && !"mail".equals(success));
        model.put(
                "inscriptionFormHiddenStyle",
                logged && hasMemberships && query == null ? "display:none" : "");

        model.put("showMembershipsSection", logged && query == null && hasMemberships);

        Double membershipDebt = model.get("membershipDebt") instanceof Number n ? n.doubleValue() : 0.0;
        boolean hasApproved = memberships.stream()
                .anyMatch(mem -> mem.getStatus() == MembershipStatus.APPROVED);
        boolean showPayment = membershipDebt > 0 && hasApproved;
        model.put("showMembershipPaymentSection", showPayment);

        boolean stripePublic = model.get("stripePublic") != null;
        boolean stripeSimulated = Boolean.TRUE.equals(model.get("stripeSimulated"));
        boolean stripeCardEnabled = !Boolean.FALSE.equals(model.get("stripeCardMembershipsEnabled"));
        boolean bankTransferEnabled = !Boolean.FALSE.equals(model.get("bankTransferMembershipsEnabled"));
        boolean ribAvailable = Boolean.TRUE.equals(model.get("ribAvailable"));

        model.put("showCardMembershipPayment", showPayment && (stripePublic || stripeSimulated) && stripeCardEnabled);
        model.put("showBankTransferMembershipPayment", showPayment && ribAvailable && bankTransferEnabled);

        model.put(
                "hasPendingConfirmationMembership",
                memberships.stream().anyMatch(mem -> mem.getStatus() == MembershipStatus.PENDING_CONFIRMATION));
        model.put(
                "hasPendingApprovalMembership",
                memberships.stream().anyMatch(mem -> mem.getStatus() == MembershipStatus.PENDING_APPROVAL));
        model.put(
                "hasApprovedMembershipInfo",
                memberships.stream().anyMatch(mem -> mem.getStatus() == MembershipStatus.APPROVED));

        @SuppressWarnings("unchecked")
        Map<String, List<MembershipOptionSubscription>> subscriptionsByMembership =
                model.get("membershipSubscriptions") instanceof Map<?, ?> map
                        ? (Map<String, List<MembershipOptionSubscription>>) map
                        : Map.of();

        List<Map<String, Object>> membershipRows = new ArrayList<>();
        for (Membership mem : memberships) {
            membershipRows.add(membershipRow(mem, subscriptionsByMembership, messages));
        }
        model.put("membershipTableRows", membershipRows);

        @SuppressWarnings("unchecked")
        List<Payment> paidMembershipPayments =
                model.get("paidMembershipPayments") instanceof List<?> list ? (List<Payment>) list : List.of();
        model.put("hasPaidMembershipPayments", !paidMembershipPayments.isEmpty());
        model.put("paidMembershipPayments", enrichPayments(paidMembershipPayments));

        @SuppressWarnings("unchecked")
        List<Payment> paidDonationPayments =
                model.get("paidDonationPayments") instanceof List<?> list ? (List<Payment>) list : List.of();
        model.put(
                "showDonationAttestations",
                logged && query == null && !paidDonationPayments.isEmpty());
        model.put("paidDonationPayments", enrichDonationPayments(paidDonationPayments));

        @SuppressWarnings("unchecked")
        List<MembershipOption> sharedOptions =
                model.get("sharedOptions") instanceof List<?> list ? (List<MembershipOption>) list : List.of();
        @SuppressWarnings("unchecked")
        Map<String, List<MembershipOptionFile>> filesByOptionId =
                model.get("sharedOptionFilesByOptionId") instanceof Map<?, ?> map
                        ? (Map<String, List<MembershipOptionFile>>) map
                        : Map.of();

        List<Map<String, Object>> sharedSections = new ArrayList<>();
        for (MembershipOption opt : sharedOptions) {
            if (opt.getId() == null) {
                continue;
            }
            List<MembershipOptionFile> files =
                    filesByOptionId.getOrDefault(String.valueOf(opt.getId()), List.of());
            if (files.isEmpty()) {
                continue;
            }
            Map<String, Object> section = new HashMap<>();
            section.put(
                    "label",
                    opt.getOptionValue() != null ? opt.getOptionValue() : String.valueOf(opt.getId()));
            List<Map<String, Object>> fileRows = new ArrayList<>();
            for (MembershipOptionFile file : files) {
                Map<String, Object> fileRow = new HashMap<>();
                fileRow.put("id", file.getId());
                fileRow.put("originalName", file.getOriginalName());
                fileRows.add(fileRow);
            }
            section.put("files", fileRows);
            sharedSections.add(section);
        }
        model.put("hasSharedFiles", !sharedSections.isEmpty());
        model.put("sharedFileSections", sharedSections);
    }

    public static void enrichMembershipOptionsPage(Map<String, Object> model, Messages messages) {
        boolean manualPlayer = Boolean.TRUE.equals(model.get("manualPlayer"));
        if (manualPlayer) {
            String firstname = stringOrEmpty(model.get("firstname"));
            String lastname = stringOrEmpty(model.get("lastname"));
            String category = stringOrEmpty(model.get("category"));
            String birthdate = formatBirthdateForSummary(stringOrEmpty(model.get("birthdate")));
            model.put(
                    "playerSummaryLine",
                    String.join(" ", firstname, lastname, category).trim() + " - " + birthdate);
        } else if (model.get("player") instanceof IPlayer player) {
            String licence = player.getLicence() != null ? player.getLicence() : stringOrEmpty(model.get("nrffe"));
            String birthdate = formatBirthdateForSummary(
                    player.getBirthDate() != null ? player.getBirthDate() : "");
            model.put(
                    "playerSummaryLine",
                    String.join(
                                    " ",
                                    stringOrEmpty(player.getFirstname()),
                                    stringOrEmpty(player.getName()),
                                    stringOrEmpty(player.getCategory()))
                            .trim()
                            + " - "
                            + birthdate
                            + " - "
                            + licence);
        }

        String selectedLicenseType =
                model.get("selectedLicenseType") instanceof String s ? s : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> licenses =
                model.get("licenses") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        for (Map<String, Object> license : licenses) {
            Object name = license.get("name");
            license.put("selected", name != null && String.valueOf(name).equals(selectedLicenseType));
            if (name != null) {
                license.put("licenseMessageKey", "clubRegister.membershipOptions.license" + name);
            }
            license.put("priceDisplay", formatEurosFromCents(license.get("priceCents")));
        }
        model.put("licenses", licenses);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options =
                model.get("options") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        for (Map<String, Object> option : options) {
            Object optionType = option.get("optionType");
            if (optionType != null) {
                option.put("optionTypeMessageKey", "membershipOption.type." + optionType);
            }
            option.put("priceDisplay", formatEurosFromCents(option.get("amountCents")));
        }
        model.put("options", options);
        model.put("hasOptions", !options.isEmpty());
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String formatBirthdateForSummary(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return "";
        }
        return isoDate.replaceAll("^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ].*)?$", "$3/$2/$1");
    }

    private static String formatEurosFromCents(Object centsObj) {
        double euros = 0.0;
        if (centsObj instanceof Number number) {
            euros = number.doubleValue() / 100.0;
        }
        return String.format(Locale.FRANCE, "%.2f", euros);
    }

    private static Map<String, Object> membershipRow(
            Membership mem,
            Map<String, List<MembershipOptionSubscription>> subscriptionsByMembership,
            Messages messages) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", mem.getId());
        row.put("nrFfe", mem.getNrFfe() != null ? mem.getNrFfe() : "");
        row.put("lastname", mem.getLastname() != null ? mem.getLastname() : "");
        row.put("firstname", mem.getFirstname() != null ? mem.getFirstname() : "");
        row.put("amountDisplay", String.format(Locale.FRANCE, "%.2f", mem.getAmountCents() / 100.0));
        if (mem.getCreatedAt() != null) {
            row.put("createdAtDisplay", messages.formatDate(mem.getCreatedAt()));
        }
        row.put(
                "statusMessageKey",
                "clubRegister.membershipStatus." + (mem.getStatus() != null ? mem.getStatus() : ""));
        row.put("showConfirmButton", mem.getStatus() == MembershipStatus.PENDING_CONFIRMATION);

        List<Map<String, Object>> badges = new ArrayList<>();
        List<MembershipOptionSubscription> subs =
                subscriptionsByMembership.getOrDefault(String.valueOf(mem.getId()), List.of());
        for (MembershipOptionSubscription mos : subs) {
            MembershipOption option = mos.getMembershipOption();
            if (option == null) {
                continue;
            }
            Map<String, Object> badge = new HashMap<>();
            badge.put(
                    "label",
                    option.getOptionValue() != null ? option.getOptionValue() : "");
            badges.add(badge);
        }
        row.put("optionBadges", badges);
        return row;
    }

    private static List<Map<String, Object>> enrichPayments(List<Payment> payments) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment payment : payments) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", payment.getId());
            String payerName = payment.getPayerName();
            row.put("payerName", payerName);
            row.put("hasPayerName", payerName != null && !payerName.isBlank());
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> enrichDonationPayments(List<Payment> payments) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Payment payment : payments) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", payment.getId());
            String payerName = payment.getPayerName();
            row.put("payerName", payerName);
            row.put("hasPayerName", payerName != null && !payerName.isBlank());
            if (payment.getAmount() != null) {
                row.put("hasAmount", true);
                row.put("amountDisplay", String.format(Locale.FRANCE, "%.2f", payment.getAmount()));
            }
            rows.add(row);
        }
        return rows;
    }
}
