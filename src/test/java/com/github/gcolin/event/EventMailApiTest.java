package com.github.gcolin.event;

import com.github.gcolin.platform.ApiTestBase;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.event.Event;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.player.Find;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.platform.SendMail;

class EventMailApiTest extends ApiTestBase {

    @Test
    void showFormShouldReturnEventWithModel() throws Exception {
        EventMailApi api = new EventMailApi();
        EventDao eventDao = mock(EventDao.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Rapid 2024");

        when(eventDao.find(1)).thenReturn(event);

        try (TestContext tc = TestContext.open(app -> {
            app.setConfig(mock(com.github.gcolin.platform.Config.class));
        }, ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPlayerSubscriptionDao(mock(PlayerSubscriptionDao.class));
        })) {

            MustacheHtml html = api.showForm(1, null);

            assertEquals("event/eventMail.mustache", html.getTemplate());
            Map<String, Object> model = html.getModel();
            assertEquals(event, model.get("event"));
        }
    }

    @Test
    void showFormShouldIncludeSentCountWhenProvided() throws Exception {
        EventMailApi api = new EventMailApi();
        EventDao eventDao = mock(EventDao.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Blitz 2024");

        when(eventDao.find(1)).thenReturn(event);

        try (TestContext tc = TestContext.open(app -> {
            app.setConfig(mock(com.github.gcolin.platform.Config.class));
        }, ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPlayerSubscriptionDao(mock(PlayerSubscriptionDao.class));
        })) {

            MustacheHtml html = api.showForm(1, 5);

            Map<String, Object> model = html.getModel();
            assertEquals(5, model.get("sent"));
        }
    }

    @Test
    void sendMailsShouldSendToMatchingSubscriptions() throws Exception {
        EventMailApi api = new EventMailApi();
        EventDao eventDao = mock(EventDao.class);
        PlayerSubscriptionDao subDao = mock(PlayerSubscriptionDao.class);
        Find find = mock(Find.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Test Event");

        PlayerSubscription sub = new PlayerSubscription();
        sub.setStatus(PlayerSubscriptionStatus.PAID);
        sub.setNrFfe("FFE123");
        sub.setCreationUser("user@test.com");

        IPlayer player = mock(IPlayer.class);
        when(player.getFullname()).thenReturn("John Doe");

        when(eventDao.find(1)).thenReturn(event);
        when(subDao.findByEvent(event)).thenReturn(List.of(sub));
        when(find.player("FFE123", null)).thenReturn(player);

        try (TestContext tc = TestContext.open(app -> {
            app.setSendMail(mock(com.github.gcolin.platform.SendMail.class));
        }, ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPlayerSubscriptionDao(subDao);
            ctx.setFind(find);
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/event/1/mail?sent=1")));

            Response response = api.sendMails(1, "Test", "Body", null, null, null, null, new ArrayList<Integer>());

            assertEquals(303, response.getStatus());
        }
    }

    @Test
    void sendMailsInBatchesShouldUseBccBatched() throws Exception {
        EventMailApi api = new EventMailApi();
        EventDao eventDao = mock(EventDao.class);
        PlayerSubscriptionDao subDao = mock(PlayerSubscriptionDao.class);
        SendMail sendMail = mock(SendMail.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Test Event");

        List<PlayerSubscription> subscriptions = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            PlayerSubscription sub = new PlayerSubscription();
            sub.setStatus(PlayerSubscriptionStatus.PAID);
            sub.setNrFfe("FFE" + i);
            sub.setCreationUser("user" + i + "@test.com");
            subscriptions.add(sub);
        }

        when(eventDao.find(1)).thenReturn(event);
        when(subDao.findByEvent(event)).thenReturn(subscriptions);
        when(sendMail.sendBccBatched(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("Test"))).thenReturn(35);

        try (TestContext tc = TestContext.open(app -> {
            app.setSendMail(sendMail);
        }, ctx -> {
            ctx.setEventDao(eventDao);
            ctx.setPlayerSubscriptionDao(subDao);
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/event/1/mail?sent=35")));

            Response response = api.sendMails(1, "Test", "Body", null, null, "true", null, new ArrayList<Integer>());

            assertEquals(303, response.getStatus());
            org.mockito.Mockito.verify(sendMail).sendBccBatched(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.argThat(list -> list.size() == 35),
                    org.mockito.ArgumentMatchers.eq("Test"));
        }
    }


}
