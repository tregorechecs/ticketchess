package com.github.gcolin.player;

import com.github.gcolin.platform.ApiTestBase;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.player.CustomPlayer;
import com.github.gcolin.event.Event;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.platform.PagedList;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.player.Find;
import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.registration.RegisterService;
import com.github.gcolin.player.CustomPlayerDao;
import com.github.gcolin.event.EventDao;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.github.gcolin.platform.MustacheHtml;

class CustomPlayerApiTest extends ApiTestBase {

    @Test
    void playersShouldPaginateAndReturnModel() throws Exception {
        CustomPlayerApi api = new CustomPlayerApi();
        CustomPlayerDao dao = mock(CustomPlayerDao.class);

        CustomPlayer p = new CustomPlayer();
        p.setName("Test");

        PagedList<CustomPlayer> paged = new PagedList<>(List.of(p), 0, 50);
        when(dao.pageSorted(0, 25)).thenReturn(paged);

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setCustomPlayerDao(dao);
        })) {

            MustacheHtml html = api.players(1, 25);
            Map<String, Object> model = html.getModel();

            assertEquals("player/players.mustache", html.getTemplate());
            assertEquals(1, model.get("currentPage"));
            assertEquals(25, model.get("pageSize"));
            assertEquals(50L, model.get("totalItems"));
            assertTrue((Boolean) model.get("hasNext"));
            assertFalse((Boolean) model.get("hasPrev"));
        }
    }

    @Test
    void newplayerShouldReturnTemplateWithDefaultPlayer() throws Exception {
        CustomPlayerApi api = new CustomPlayerApi();

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {
            MustacheHtml html = api.newplayer(null);

            assertEquals("player/customplayer.mustache", html.getTemplate());
            CustomPlayer player = (CustomPlayer) html.getModel().get("player");
            assertEquals("", player.getName());
            assertEquals("", player.getElo());
        }
    }

    @Test
    void editWithIdShouldReturnPlayerFromDao() throws Exception {
        CustomPlayerApi api = new CustomPlayerApi();
        CustomPlayerDao dao = mock(CustomPlayerDao.class);

        CustomPlayer player = new CustomPlayer();
        player.setId(3);
        player.setName("Doe");

        when(dao.find(3)).thenReturn(player);

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setCustomPlayerDao(dao);
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {

            MustacheHtml html = api.edit(3, null);

            assertEquals("player/customplayer.mustache", html.getTemplate());
            assertEquals(player, html.getModel().get("player"));
        }
    }

    @Test
    void saveShouldPersistNewCustomPlayerAndRedirectToMyEvents() throws Exception {
        CustomPlayerApi api = new CustomPlayerApi();
        CustomPlayerDao dao = mock(CustomPlayerDao.class);
        LoggedUser user = mock(LoggedUser.class);

        when(user.getEmail()).thenReturn("user@test.com");
        when(user.isAdmin()).thenReturn(false);

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
        }, ctx -> {
            ctx.setCustomPlayerDao(dao);
            ctx.setLoggedUser(user);
            ctx.setRegisterService(mock(RegisterService.class));
            ctx.setEventDao(mock(EventDao.class));
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/event/my?success=register")));

            Response response = api.save(null, null, "LIC", "Smith", "1990", "John", "true", "1800");

            assertEquals(303, response.getStatus());
            verify(dao).persist(org.mockito.ArgumentMatchers.any(CustomPlayer.class));
        }
    }

    @Test
    void saveShouldRegisterToEventWhenEventIdProvided() throws Exception {
        CustomPlayerApi api = new CustomPlayerApi();
        CustomPlayerDao dao = mock(CustomPlayerDao.class);
        LoggedUser user = mock(LoggedUser.class);
        RegisterService registerService = mock(RegisterService.class);
        EventDao eventDao = mock(EventDao.class);

        Event event = new Event();
        event.setId(5);

        PlayerSubscription sub = new PlayerSubscription();
        sub.setStatus(PlayerSubscriptionStatus.PAID);

        when(user.getEmail()).thenReturn("user@test.com");
        when(user.isAdmin()).thenReturn(false);
        when(eventDao.find(5)).thenReturn(event);
        when(registerService.registerPlayerToEvent(
                        org.mockito.ArgumentMatchers.eq(event),
                        org.mockito.ArgumentMatchers.any(CustomPlayer.class),
                        org.mockito.ArgumentMatchers.eq("user@test.com")))
                .thenReturn(sub);

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
        }, ctx -> {
            ctx.setCustomPlayerDao(dao);
            ctx.setLoggedUser(user);
            ctx.setRegisterService(registerService);
            ctx.setEventDao(eventDao);
            ctx.setFind(mock(Find.class));
        })) {
            RequestContext.require().setUriInfo(mockUriInfo(URI.create("http://localhost:8080/event/5?success=register")));

            Response response = api.save(null, 5, "LIC", "Smith", "1990", "John", "false", "1600");

            assertEquals(303, response.getStatus());
        }
    }


}
