package com.github.gcolin.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.membership.Membership;
import com.github.gcolin.membership.MembershipOption;
import com.github.gcolin.membership.MembershipOptionSubscription;
import com.github.gcolin.membership.MembershipStatus;
import com.github.gcolin.platform.Messages;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.player.IPlayer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ClubRegisterMustacheEnricherTest {

    @Test
    void enrichShowsMembershipPaymentRowsAndSharedFiles() {
        Map<String, Object> model = new HashMap<>();
        model.put("memberships", List.of(membership(MembershipStatus.APPROVED)));
        model.put("membershipDebt", 25.0);
        model.put("stripePublic", "pk_test");
        model.put("stripeCardMembershipsEnabled", true);
        model.put("bankTransferMembershipsEnabled", true);
        model.put("ribAvailable", true);
        model.put("paidMembershipPayments", List.of(payment("Alice")));
        model.put("paidDonationPayments", List.of(payment("Donor")));
        model.put("membershipSubscriptions", Map.of("1", List.of(subscription())));
        MembershipOption option = new MembershipOption();
        option.setId(4);
        option.setOptionValue("Documents");
        com.github.gcolin.membership.MembershipOptionFile file =
                new com.github.gcolin.membership.MembershipOptionFile();
        file.setId(8);
        file.setOriginalName("rules.pdf");
        model.put("sharedOptions", List.of(option));
        model.put("sharedOptionFilesByOptionId", Map.of("4", List.of(file)));

        LoggedUser user = mock(LoggedUser.class);
        when(user.getEmail()).thenReturn("user@test.com");
        ClubRegisterMustacheEnricher.enrich(model, new Messages(Locale.FRANCE), user);

        assertTrue((Boolean) model.get("showMembershipPaymentSection"));
        assertTrue((Boolean) model.get("showCardMembershipPayment"));
        assertTrue((Boolean) model.get("showBankTransferMembershipPayment"));
        assertTrue((Boolean) model.get("showDonationAttestations"));
        assertTrue((Boolean) model.get("hasSharedFiles"));
        assertEquals(1, ((List<?>) model.get("membershipTableRows")).size());
        assertEquals(1, ((List<?>) model.get("sharedFileSections")).size());
    }

    @Test
    void enrichMembershipOptionsSupportsManualAndKnownPlayers() {
        Messages messages = new Messages(Locale.FRANCE);
        Map<String, Object> manual = new HashMap<>();
        manual.put("manualPlayer", true);
        manual.put("firstname", "Ada");
        manual.put("lastname", "Lovelace");
        manual.put("category", "S");
        manual.put("birthdate", "2010-02-03");
        manual.put("licenses", List.of(new HashMap<>(Map.of("name", "A", "priceCents", 1200))));
        manual.put("options", List.of(new HashMap<>(Map.of("optionType", "COURSES", "amountCents", 500))));
        ClubRegisterMustacheEnricher.enrichMembershipOptionsPage(manual, messages);
        assertEquals("Ada Lovelace S - 03/02/2010", manual.get("playerSummaryLine"));
        assertTrue((Boolean) manual.get("hasOptions"));

        Map<String, Object> known = new HashMap<>();
        IPlayer player = mock(IPlayer.class);
        when(player.getFirstname()).thenReturn("Grace");
        when(player.getName()).thenReturn("Hopper");
        when(player.getCategory()).thenReturn("A");
        when(player.getBirthDate()).thenReturn("2000-01-02");
        when(player.getLicence()).thenReturn("LIC");
        known.put("player", player);
        ClubRegisterMustacheEnricher.enrichMembershipOptionsPage(known, messages);
        assertEquals("Grace Hopper A - 02/01/2000 - LIC", known.get("playerSummaryLine"));
    }

    private static Membership membership(MembershipStatus status) {
        Membership membership = new Membership();
        membership.setId(1);
        membership.setFirstname("Ada");
        membership.setLastname("Lovelace");
        membership.setUser("ada@test.com");
        membership.setStatus(status);
        membership.setAmountCents(2500);
        membership.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        return membership;
    }

    private static Payment payment(String payer) {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPayerName(payer);
        return payment;
    }

    private static MembershipOptionSubscription subscription() {
        MembershipOption option = new MembershipOption();
        option.setOptionValue("Course");
        MembershipOptionSubscription subscription = new MembershipOptionSubscription();
        subscription.setMembershipOption(option);
        return subscription;
    }
}
