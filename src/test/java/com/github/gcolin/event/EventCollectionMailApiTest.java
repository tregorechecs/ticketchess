package com.github.gcolin.event;

import com.github.gcolin.platform.ApiTestBase;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.player.Find;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.Page;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import io.jsonwebtoken.security.Keys;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventCollectionMailApiTest extends ApiTestBase {
    @Test
    void showFormAndPreview() throws Exception {
        EventCollectionMailApi api = new EventCollectionMailApi();
        EventCollectionDao dao = mock(EventCollectionDao.class);
        EventCollection collection = collection(1, "Open, Cup");
        when(dao.find(1)).thenReturn(collection);

        Config config = mock(Config.class);
        Page page = new Page();
        page.setOrgName("Chess Club");
        when(config.getPage()).thenReturn(page);
        try (TestContext tc = TestContext.open(app -> {
            app.setConfig(config);
        }, ctx -> {
            ctx.setEventCollectionDao(dao);
        })) {
            assertEquals("event/eventcollectionMail.mustache", api.showForm(1, 7).getTemplate());
            assertEquals(7, api.showForm(1, 7).getModel().get("sent"));
            Response preview = api.preview(1, "**hello**", false);
            assertEquals(200, preview.getStatus());
            assertInstanceOf(String.class, preview.getEntity());
            assertEquals(200, api.preview(1, null, true).getStatus());
        }
    }

    @Test
    void sendIndividualMailsAppliesStatusAndEloFilters() throws Exception {
        EventCollection c = collection(1, "Cup");
        PlayerSubscription good = sub(1, "FFE1", "a@x", PlayerSubscriptionStatus.PAID);
        PlayerSubscription cancelled = sub(2, "FFE2", "b@x", PlayerSubscriptionStatus.CANCELLED);
        PlayerSubscription low = sub(3, "FFE3", "c@x", PlayerSubscriptionStatus.PAID);
        try (Fixture fx = new Fixture()) {
            when(fx.eventCollectionDao.find(1)).thenReturn(c);
            when(fx.subs.findByEventCollection(1)).thenReturn(List.of(good, cancelled, low));
            IPlayer goodPlayer = player("Good", "One", "1500");
            IPlayer lowPlayer = player("Low", "One", "900");
            when(fx.find.player("FFE1", null)).thenReturn(goodPlayer);
            when(fx.find.player("FFE3", null)).thenReturn(lowPlayer);
            Response response = fx.api.sendMails(1, "S", "body", null, 1000, 1600, null);
            assertEquals(303, response.getStatus());
            verify(fx.sendMail).send(any(), eq("a@x"), eq("S"));
        }
    }

    @Test
    void sendBccMailsAndInvalidFilters() throws Exception {
        EventCollection c = collection(1, "Cup");
        PlayerSubscription a = sub(1, "FFE1", "a@x", PlayerSubscriptionStatus.PAID);
        PlayerSubscription b = sub(2, "FFE2", "", PlayerSubscriptionStatus.PAID);
        try (Fixture fx = new Fixture()) {
            when(fx.eventCollectionDao.find(1)).thenReturn(c);
            when(fx.subs.findByEventCollection(1)).thenReturn(List.of(a, b));
            Response response = fx.api.sendMails(1, "S", "body", "bad-status", null, null, "true");
            assertEquals(303, response.getStatus());
            verify(fx.sendMail, never()).sendBcc(any(), anyList(), anyString());
            response = fx.api.sendMails(1, "S", "body", null, null, null, "true");
            assertEquals(303, response.getStatus());
            verify(fx.sendMail).sendBcc(any(), eq(List.of("a@x")), eq("S"));
        }
    }

    @Test
    void missingCollectionIsNotFound() throws Exception {
        try (Fixture fx = new Fixture()) {
            when(fx.eventCollectionDao.find(9)).thenReturn(null);
            org.junit.jupiter.api.Assertions.assertThrows(NotFoundException.class, () -> fx.api.showForm(9, null));
            org.junit.jupiter.api.Assertions.assertThrows(NotFoundException.class, () -> fx.api.preview(9, "", false));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final EventCollectionMailApi api = new EventCollectionMailApi();
        final EventCollectionDao eventCollectionDao = mock(EventCollectionDao.class);
        final PlayerSubscriptionDao subs = mock(PlayerSubscriptionDao.class);
        final Find find = mock(Find.class);
        final SendMail sendMail = mock(SendMail.class);
        final TestContext tc;

        Fixture() throws Exception {
            Config config = mock(Config.class);
            when(config.getKeys()).thenReturn(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes()));
            Page page = new Page();
            page.setOrgName("Club");
            when(config.getPage()).thenReturn(page);
            tc = TestContext.open(app -> {
                app.setConfig(config);
                app.setSendMail(sendMail);
            }, ctx -> {
                ctx.setEventCollectionDao(eventCollectionDao);
                ctx.setPlayerSubscriptionDao(subs);
                ctx.setFind(find);
            });
            RequestContext.require().setUriInfo(mockUriInfo());
        }

        @Override
        public void close() {
            tc.close();
        }
    }

    private static EventCollection collection(int id, String name) {
        EventCollection c = new EventCollection();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static PlayerSubscription sub(int id, String ffe, String email, PlayerSubscriptionStatus status) {
        PlayerSubscription s = new PlayerSubscription();
        s.setId(id); s.setNrFfe(ffe); s.setCreationUser(email); s.setStatus(status);
        return s;
    }

    private static IPlayer player(String name, String first, String rating) {
        IPlayer p = mock(IPlayer.class);
        when(p.getName()).thenReturn(name); when(p.getFirstname()).thenReturn(first);
        when(p.getFullname()).thenReturn(first + " " + name); when(p.getRating()).thenReturn(rating);
        return p;
    }

    private static UriInfo mockUriInfo() {
        UriInfo info = mock(UriInfo.class); UriBuilder builder = mock(UriBuilder.class);
        when(info.getBaseUri()).thenReturn(URI.create("http://localhost/"));
        when(info.getBaseUriBuilder()).thenReturn(builder);
        when(builder.path(anyString())).thenReturn(builder);
        when(builder.queryParam(anyString(), anyInt())).thenReturn(builder);
        when(builder.build(anyInt())).thenReturn(URI.create("http://localhost/eventcollection/1/mail?sent=1"));
        return info;
    }

}
