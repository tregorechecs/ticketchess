package com.github.gcolin.event;

import com.github.gcolin.platform.ApiTestBase;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.event.EventGroup;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.event.EventGroupDao;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;

class EventGroupApiTest extends ApiTestBase {

    @Test
    void listShouldReturnAllEventGroupsSorted() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        EventGroup eg1 = new EventGroup("Open");
        eg1.setId(1);
        EventGroup eg2 = new EventGroup("Blitz");
        eg2.setId(2);

        java.util.ArrayList<EventGroup> list = new java.util.ArrayList<>(List.of(eg2, eg1));
        when(dao.all()).thenReturn(list);

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventGroupDao(dao);
        })) {

            MustacheHtml html = api.list();

            assertEquals("event/eventgroup.mustache", html.getTemplate());
            @SuppressWarnings("unchecked")
            List<EventGroup> groups = (List<EventGroup>) html.getModel().get("eventGroups");
            assertEquals(2, groups.size());
            assertEquals("Blitz", groups.get(0).getName());
            assertEquals("Open", groups.get(1).getName());
        }
    }

    @Test
    void newEventGroupShouldReturnEmptyTemplate() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        when(dao.all()).thenReturn(new java.util.ArrayList<>());

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventGroupDao(dao);
        })) {

            MustacheHtml html = api.newEventGroup();

            assertEquals("event/eventgroupEdit.mustache", html.getTemplate());
            assertTrue(html.getModel().get("eventGroup") instanceof EventGroup);
        }
    }

    @Test
    void editShouldReturnEventGroupWithEvents() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        EventGroup eg = new EventGroup("Open");
        eg.setId(1);
        eg.setEvents(List.of());

        when(dao.findDetachedForEdit(1)).thenReturn(eg);

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventGroupDao(dao);
        })) {

            MustacheHtml html = api.edit(1);

            assertEquals("event/eventgroupEdit.mustache", html.getTemplate());
            Map<String, Object> model = html.getModel();
            assertEquals(eg, model.get("eventGroup"));
            assertTrue(((List<?>) model.get("events")).isEmpty());
        }
    }

    @Test
    void saveShouldPersistNewEventGroupAndRedirect() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        EventGroup eg = new EventGroup("Rapid");
        eg.setId(10);

        doAnswer(invocation -> {
                    EventGroup arg = invocation.getArgument(0);
                    arg.setId(10);
                    return null;
                })
                .when(dao)
                .persist(org.mockito.ArgumentMatchers.any(EventGroup.class));

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
        }, ctx -> {
            ctx.setEventGroupDao(dao);
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/eventgroup/10")));

            Response response = api.save(null, null, "rapid", "Rapid");

            assertEquals(303, response.getStatus());
            verify(dao).persist(org.mockito.ArgumentMatchers.any(EventGroup.class));
        }
    }

    @Test
    void saveShouldMergeExistingEventGroupAndRedirect() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        EventGroup eg = new EventGroup("Blitz Updated");
        eg.setId(5);

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
        }, ctx -> {
            ctx.setEventGroupDao(dao);
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/eventgroup/5")));

            Response response = api.save(null, 5, "blitz", "Blitz Updated");

            assertEquals(303, response.getStatus());
            verify(dao).merge(org.mockito.ArgumentMatchers.any(EventGroup.class));
        }
    }

    @Test
    void saveShouldRemoveEventGroupWhenActionIsRemove() throws Exception {
        EventGroupApi api = new EventGroupApi();
        EventGroupDao dao = mock(EventGroupDao.class);

        EventGroup eg = new EventGroup("Open");
        eg.setId(9);
        when(dao.find(9)).thenReturn(eg);

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
        }, ctx -> {
            ctx.setEventGroupDao(dao);
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/admin/events")));

            Response response = api.save("remove", 9, null, null);

            assertEquals(303, response.getStatus());
            verify(dao).remove(eg);
        }
    }


}
