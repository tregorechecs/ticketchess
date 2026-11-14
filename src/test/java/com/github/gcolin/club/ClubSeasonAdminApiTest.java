package com.github.gcolin.club;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.platform.MustacheHtml;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClubSeasonAdminApiTest {
    @Test
    void listAndPagesCoverExistingAndMissingSeasons() throws Exception {
        ClubSeasonAdminApi api = new ClubSeasonAdminApi();
        ClubSeasonDao dao = mock(ClubSeasonDao.class);
        ClubSeason season = new ClubSeason();
        season.setId(2);
        season.setName("2026");
        when(dao.allOrderByStartDateDesc()).thenReturn(List.of(season));
        when(dao.find(2)).thenReturn(season);
        when(dao.find(9)).thenReturn(null);
        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setClubSeasonDao(dao);
        })) {
            assertEquals("club/seasonAdmin.mustache", api.list().getTemplate());
            assertEquals("club/seasonEdit.mustache", api.createPage().getTemplate());
            assertEquals("club/seasonEdit.mustache", api.editPage(2).getTemplate());
            MustacheHtml missing = api.editPage(9);
            assertEquals("platform/error.mustache", missing.getTemplate());
            assertEquals(404, missing.getModel().get("statusCode"));
        }
    }

    @Test
    void createUpdateDeleteRedirectAndPersistDates() throws Exception {
        ClubSeasonAdminApi api = new ClubSeasonAdminApi();
        ClubSeasonDao dao = mock(ClubSeasonDao.class);
        ClubSeason existing = new ClubSeason();
        existing.setId(4);
        when(dao.find(4)).thenReturn(existing);
        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setClubSeasonDao(dao);
        })) {
            RequestContext.require().setUriInfo(uriInfo());
            assertEquals(303, api.create("Winter", "2026-01-01", "2026-06-30", "on").getStatus());
            verify(dao).save(any(ClubSeason.class), org.mockito.ArgumentMatchers.eq(true));
            assertEquals(303, api.update(4, "Summer", "2026-07-01", "2026-12-31", "true").getStatus());
            assertEquals("Summer", existing.getName());
            verify(dao).save(existing, true);
            assertEquals(404, api.update(8, "x", "2026-01-01", "2026-01-02", null).getStatus());
            assertEquals(303, api.delete(4).getStatus());
            verify(dao).remove(4);
        }
    }

    private static UriInfo uriInfo() {
        UriInfo info = mock(UriInfo.class);
        UriBuilder builder = mock(UriBuilder.class);
        when(info.getBaseUriBuilder()).thenReturn(builder);
        when(builder.path("admin/seasons")).thenReturn(builder);
        when(builder.build()).thenReturn(URI.create("http://localhost/admin/seasons"));
        return info;
    }

}
