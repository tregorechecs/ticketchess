package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.player.Find;
import com.github.gcolin.player.LuceneDb;
import com.github.gcolin.player.Player;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventLicenceCheckApiTest {
    @Test
    void standardRequiresLicenceAAndEscapesCsv() throws Exception {
        try (Fixture fx = new Fixture()) {
            EventLicenceCheckApi api = fx.api;
            Event event = event(EventType.STANDARD, "Cup; final");
            when(fx.dao.find(1)).thenReturn(event);
            PlayerSubscription bad = sub(11, "P1");
            PlayerSubscription good = sub(12, "P2");
            PlayerSubscription custom = sub(13, "@custom");
            PlayerSubscription empty = sub(14, "");
            when(fx.subs.findByEvent(event)).thenReturn(List.of(bad, good, custom, empty));
            Player p1 = player("Doe; \"D\"", "Jane\nX", "B", "FFE");
            Player p2 = player("Good", "Player", "A", "FFE");
            when(fx.lucene.searchJoueur("P1")).thenReturn(p1);
            when(fx.lucene.searchJoueur("P2")).thenReturn(p2);
            when(fx.find.player("@custom", null)).thenReturn(player("Custom", "User", null, null));
            String csv = (String) api.licenceCheck(1).getEntity();
            assertTrue(csv.contains("\"Doe; \"\"D\"\" Jane\nX\";\"Cup; final\";1;11;licence A manquante"));
            assertTrue(csv.contains("Custom User;\"Cup; final\";1;13;joueur custom"));
            assertFalse(csv.contains("12;"));
            assertEquals("text/csv;charset=UTF-8", api.licenceCheck(1).getMediaType().toString());
        }
    }

    @Test
    void rapidAndBlitzRequireFfeOrFideAndHandleMissingPlayers() throws Exception {
        try (Fixture fx = new Fixture()) {
            EventLicenceCheckApi api = fx.api;
            Event event = event(EventType.RAPID, "Rapid");
            when(fx.dao.find(2)).thenReturn(event);
            PlayerSubscription noIds = sub(21, "P1");
            PlayerSubscription fide = sub(22, "P2");
            PlayerSubscription absent = sub(23, "P3");
            PlayerSubscription thrown = sub(24, "P4");
            when(fx.subs.findByEvent(event)).thenReturn(List.of(noIds, fide, absent, thrown));
            when(fx.lucene.searchJoueur("P1")).thenReturn(player("No", "Id", null, null));
            when(fx.lucene.searchJoueur("P2")).thenReturn(player("Fide", "User", null, "123"));
            when(fx.lucene.searchJoueur("P3")).thenReturn(null);
            when(fx.find.player("P3", null)).thenReturn(player("Fallback", "Name", null, null));
            when(fx.lucene.searchJoueur("P4")).thenThrow(new RuntimeException("bad"));
            String csv = (String) api.licenceCheck(2).getEntity();
            assertTrue(csv.contains("No Id;Rapid;2;21;sans numéro FFE/FIDE"));
            assertFalse(csv.contains("22;"));
            assertTrue(csv.contains("Fallback Name;Rapid;2;23;joueur introuvable"));
            assertTrue(csv.contains("P4;Rapid;2;24;joueur introuvable"));
        }
    }

    @Test
    void unknownEventReturnsNotFound() throws Exception {
        try (Fixture fx = new Fixture()) {
            when(fx.dao.find(99)).thenReturn(null);
            assertEquals(404, assertThrows(WebApplicationException.class, () -> fx.api.licenceCheck(99))
                    .getResponse().getStatus());
        }
    }

    private static Event event(EventType type, String name) {
        Event e = new Event(); e.setId(1); e.setName(name); e.setEventType(type); return e;
    }
    private static PlayerSubscription sub(int id, String ffe) {
        PlayerSubscription s = new PlayerSubscription(); s.setId(id); s.setNrFfe(ffe); return s;
    }
    private static Player player(String name, String first, String aff, String ffe) {
        Player p = new Player(); p.setName(name); p.setFirstname(first); p.setAffType(aff); p.setNrffe(ffe); return p;
    }

    private static final class Fixture implements AutoCloseable {
        final EventLicenceCheckApi api = new EventLicenceCheckApi();
        final EventDao dao = mock(EventDao.class);
        final PlayerSubscriptionDao subs = mock(PlayerSubscriptionDao.class);
        final LuceneDb lucene = mock(LuceneDb.class);
        final Find find = mock(Find.class);
        final TestContext tc = TestContext.open(
                app -> app.setLuceneDb(lucene),
                ctx -> {
                    ctx.setEventDao(dao);
                    ctx.setPlayerSubscriptionDao(subs);
                    ctx.setFind(find);
                });

        @Override
        public void close() {
            tc.close();
        }
    }
}
