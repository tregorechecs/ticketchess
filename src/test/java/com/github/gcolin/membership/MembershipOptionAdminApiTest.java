package com.github.gcolin.membership;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.club.*;
import com.github.gcolin.platform.TestContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MembershipOptionAdminApiTest {
    @Test void pagesAndCrud() throws Exception {
        MembershipOptionAdminApi api = new MembershipOptionAdminApi();
        MembershipOptionDao options = mock(MembershipOptionDao.class);
        LicenseDao licenses = mock(LicenseDao.class); ClubSeasonFilter filter = mock(ClubSeasonFilter.class);
        ClubSeasonDao seasons = mock(ClubSeasonDao.class); UriInfo uri = mock(UriInfo.class);
        when(uri.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));
        ClubSeason season = new ClubSeason(); season.setId(8); season.setName("2026");
        season.setStartDate(LocalDate.of(2026, 1, 1)); season.setEndDate(LocalDate.of(2026, 12, 31));
        when(filter.resolve(null)).thenReturn(SeasonScope.of(season)); when(filter.effectiveSeasonId(any())).thenReturn(8);
        when(filter.effectiveSeasonId(8)).thenReturn(8); when(seasons.find(8)).thenReturn(season);
        when(options.all(any())).thenReturn(List.of()); when(licenses.all()).thenReturn(List.of());
        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setMembershipOptionDao(options);
            ctx.setClubSeasonFilter(filter);
            ctx.setLicenseDao(licenses);
            ctx.setClubSeasonDao(seasons);
        })) {
            RequestContext.require().setUriInfo(uri);
            assertEquals("membership/membershipOption.mustache", api.list(null).getTemplate());
            assertEquals("membership/membershipOptionEdit.mustache", api.createPage(null).getTemplate());
            when(options.find(1)).thenReturn(new MembershipOption());
            assertEquals("membership/membershipOptionEdit.mustache", api.editPage(1, 8).getTemplate());
            when(options.find(9)).thenReturn(null);
            assertEquals(404, api.editPage(9, null).getModel().get("statusCode"));
            assertEquals(303, api.create("COURSES", "x", 10, "", 0, 8).getStatus());
            verify(options).persist(any(MembershipOption.class));
            MembershipOption option = new MembershipOption(); when(options.find(2)).thenReturn(option);
            assertEquals(303, api.update(2, "COURSES", "y", 20, null, 0, 8).getStatus());
            verify(options).merge(option);
            assertEquals(404, api.update(3, "COURSES", "x", 1, "ALL", 0, null).getStatus());
            assertEquals(303, api.delete(2, 8).getStatus()); verify(options).remove(2);
        }
    }
}
