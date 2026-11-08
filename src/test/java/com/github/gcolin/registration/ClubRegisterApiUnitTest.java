package com.github.gcolin.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.membership.LicensePriceService;
import com.github.gcolin.membership.Membership;
import com.github.gcolin.membership.MembershipDao;
import com.github.gcolin.membership.MembershipOption;
import com.github.gcolin.membership.MembershipOptionFile;
import com.github.gcolin.membership.MembershipOptionFileDao;
import com.github.gcolin.membership.MembershipOptionFileService;
import com.github.gcolin.membership.MembershipOptionSubscriptionDao;
import com.github.gcolin.membership.MembershipOptionSubscription;
import com.github.gcolin.membership.MembershipStatus;
import com.github.gcolin.platform.Caches;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClubRegisterApiUnitTest {

    @Test
    void downloadSharedFileChecksAccessAndStreamsMetadata() throws Exception {
        ClubRegisterApi api = new ClubRegisterApi();
        MembershipOptionFileDao files = mock(MembershipOptionFileDao.class);
        MembershipOptionFileService service = mock(MembershipOptionFileService.class);
        MembershipDao memberships = mock(MembershipDao.class);
        MembershipOptionSubscriptionDao subscriptions = mock(MembershipOptionSubscriptionDao.class);
        LoggedUser user = mock(LoggedUser.class);
        when(user.getEmail()).thenReturn("member@test.com");
        Membership membership = new Membership();
        membership.setId(4);
        MembershipOption option = new MembershipOption();
        option.setId(7);
        MembershipOptionFile file = new MembershipOptionFile();
        file.setMembershipOption(option);
        file.setOriginalName("my file.pdf");
        file.setContentType("application/pdf");
        Path path = Files.createTempFile("club-register-test", ".pdf");
        try {
            when(files.findWithOption(1)).thenReturn(file);
            when(memberships.findByUser("member@test.com")).thenReturn(List.of(membership));
            when(subscriptions.findByMembershipIds(List.of(4))).thenReturn(List.of(subscription(option)));
            when(service.resolveStoredFile(file)).thenReturn(path);
            inject(api, "membershipOptionFileDao", files);
            inject(api, "membershipOptionFileService", service);
            inject(api, "membershipDao", memberships);
            inject(api, "membershipOptionSubscriptionDao", subscriptions);
            inject(api, "loggedUser", user);

            Response response = api.downloadSharedFile(1);
            assertEquals(200, response.getStatus());
            assertEquals("application/pdf", response.getMediaType().toString());
            assertEquals("attachment; filename*=UTF-8''my%20file.pdf",
                    response.getHeaderString("Content-Disposition"));

            when(subscriptions.findByMembershipIds(List.of(4))).thenReturn(List.of());
            assertThrowsStatus(403, () -> api.downloadSharedFile(1));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void confirmMembershipApprovesOwnedPendingMembership() throws Exception {
        ClubRegisterApi api = new ClubRegisterApi();
        MembershipDao dao = mock(MembershipDao.class);
        LoggedUser user = mock(LoggedUser.class);
        Caches caches = new Caches();
        Membership membership = new Membership();
        membership.setId(8);
        membership.setUser("member@test.com");
        membership.setStatus(MembershipStatus.PENDING_CONFIRMATION);
        when(user.getEmail()).thenReturn("member@test.com");
        when(dao.find(8)).thenReturn(membership);
        inject(api, "membershipDao", dao);
        inject(api, "loggedUser", user);
        inject(api, "caches", caches);
        inject(api, "uriInfo", uriInfo());

        assertEquals(303, api.confirmMembership(8).getStatus());
        assertEquals(MembershipStatus.APPROVED, membership.getStatus());
        verify(dao).merge(membership);
    }

    @Test
    void confirmMembershipRejectsMissingAndForeignMemberships() throws Exception {
        ClubRegisterApi api = new ClubRegisterApi();
        MembershipDao dao = mock(MembershipDao.class);
        LoggedUser user = mock(LoggedUser.class);
        when(user.getEmail()).thenReturn("other@test.com");
        inject(api, "membershipDao", dao);
        inject(api, "loggedUser", user);
        when(dao.find(1)).thenReturn(null);
        assertThrowsStatus(404, () -> api.confirmMembership(1));
        Membership membership = new Membership();
        membership.setUser("owner@test.com");
        when(dao.find(1)).thenReturn(membership);
        assertThrowsStatus(403, () -> api.confirmMembership(1));
    }

    @Test
    void licensePriceAndCategoryHelpersHandleInvalidAndValidDates() throws Exception {
        ClubRegisterApi api = new ClubRegisterApi();
        LicensePriceService prices = mock(LicensePriceService.class);
        when(prices.getLicensePrice(anyString(), any(Character.class))).thenReturn(1234);
        inject(api, "licensePriceService", prices);
        Method method = ClubRegisterApi.class.getDeclaredMethod("calculateLicensePriceCents", String.class, char.class);
        method.setAccessible(true);
        assertNull(method.invoke(api, "xx", 'A'));
        assertEquals(1234, method.invoke(api, "2010-01-01", 'A'));
        assertNull(method.invoke(api, "20x0", 'A'));
    }

    @Test
    void downloadSharedFileRejectsMissingOption() throws Exception {
        ClubRegisterApi api = new ClubRegisterApi();
        MembershipOptionFileDao dao = mock(MembershipOptionFileDao.class);
        when(dao.findWithOption(2)).thenReturn(null);
        inject(api, "membershipOptionFileDao", dao);
        assertThrowsStatus(404, () -> api.downloadSharedFile(2));
    }

    private static MembershipOptionSubscription subscription(MembershipOption option) {
        MembershipOptionSubscription sub = new MembershipOptionSubscription();
        sub.setMembershipOption(option);
        return sub;
    }

    private static UriInfo uriInfo() {
        UriInfo info = mock(UriInfo.class);
        UriBuilder builder = mock(UriBuilder.class);
        when(info.getBaseUriBuilder()).thenReturn(builder);
        when(builder.path(anyString())).thenReturn(builder);
        when(builder.queryParam(anyString(), any())).thenReturn(builder);
        when(builder.build()).thenReturn(URI.create("http://localhost/club-register"));
        return info;
    }

    private static void assertThrowsStatus(int status, Runnable action) {
        try {
            action.run();
        } catch (WebApplicationException e) {
            assertEquals(status, e.getResponse().getStatus());
            return;
        }
        throw new AssertionError("Expected HTTP " + status);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
