package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;

class EventAdminApiTest {

    @Test
    void pageShouldRenderAdminEventsTree() throws Exception {
        EventAdminApi api = new EventAdminApi();
        EventDao eventDao = mock(EventDao.class);
        EventGroupDao eventGroupDao = mock(EventGroupDao.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Test event");

        when(eventDao.findAllForAdmin(any(SeasonScope.class))).thenReturn(List.of(event));
        when(eventGroupDao.all()).thenReturn(List.of());

        ClubSeasonFilter clubSeasonFilter = mock(ClubSeasonFilter.class);
        when(clubSeasonFilter.resolve(null)).thenReturn(SeasonScope.all());
        doAnswer(invocation -> {
            Map<String, Object> model = invocation.getArgument(0);
            model.put("seasons", List.of());
            model.put("seasonId", invocation.getArgument(1));
            return null;
        }).when(clubSeasonFilter).addToModel(any(), any());

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setEventGroupDao(eventGroupDao);
            ctx.setClubSeasonFilter(clubSeasonFilter);
        })) {

            MustacheHtml html = api.page(null);

            assertEquals("event/eventAdminTree.mustache", html.getTemplate());
            verify(eventDao).fillNbSubscriptions(List.of(event));
            verify(eventDao).detachAll(List.of(event));
        }
    }
}
