package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.player.Find;
import com.github.gcolin.player.LuceneDb;
import com.github.gcolin.player.Player;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventLicenceCheckApiTest {
    @Test
    void standardRequiresLicenceAAndEscapesCsv() throws Exception {
        EventLicenceCheckApi api = configured();
        Event event = event(EventType.STANDARD, "Cup; final");
        when(dao(api).find(1)).thenReturn(event);
        PlayerSubscription bad = sub(11, "P1");
        PlayerSubscription good = sub(12, "P2");
        PlayerSubscription custom = sub(13, "@custom");
        PlayerSubscription empty = sub(14, "");
        when(subs(api).findByEvent(event)).thenReturn(List.of(bad, good, custom, empty));
        Player p1 = player("Doe; \"D\"", "Jane\nX", "B", "FFE");
        Player p2 = player("Good", "Player", "A", "FFE");
        when(lucene(api).searchJoueur("P1")).thenReturn(p1);
        when(lucene(api).searchJoueur("P2")).thenReturn(p2);
        when(find(api).player("@custom", null)).thenReturn(player("Custom", "User", null, null));
        String csv = (String) api.licenceCheck(1).getEntity();
        assertTrue(csv.contains("\"Doe; \"\"D\"\" Jane\nX\";\"Cup; final\";1;11;licence A manquante"));
        assertTrue(csv.contains("Custom User;\"Cup; final\";1;13;joueur custom"));
        assertFalse(csv.contains("12;"));
        assertEquals("text/csv;charset=UTF-8", api.licenceCheck(1).getMediaType().toString());
    }

    @Test
    void rapidAndBlitzRequireFfeOrFideAndHandleMissingPlayers() throws Exception {
        EventLicenceCheckApi api = configured();
        Event event = event(EventType.RAPID, "Rapid");
        when(dao(api).find(2)).thenReturn(event);
        PlayerSubscription noIds = sub(21, "P1");
        PlayerSubscription fide = sub(22, "P2");
        PlayerSubscription absent = sub(23, "P3");
        PlayerSubscription thrown = sub(24, "P4");
        when(subs(api).findByEvent(event)).thenReturn(List.of(noIds, fide, absent, thrown));
        when(lucene(api).searchJoueur("P1")).thenReturn(player("No", "Id", null, null));
        when(lucene(api).searchJoueur("P2")).thenReturn(player("Fide", "User", null, "123"));
        when(lucene(api).searchJoueur("P3")).thenReturn(null);
        when(find(api).player("P3", null)).thenReturn(player("Fallback", "Name", null, null));
        when(lucene(api).searchJoueur("P4")).thenThrow(new RuntimeException("bad"));
        String csv = (String) api.licenceCheck(2).getEntity();
        assertTrue(csv.contains("No Id;Rapid;2;21;sans numéro FFE/FIDE"));
        assertFalse(csv.contains("22;"));
        assertTrue(csv.contains("Fallback Name;Rapid;2;23;joueur introuvable"));
        assertTrue(csv.contains("P4;Rapid;2;24;joueur introuvable"));
    }

    @Test
    void unknownEventReturnsNotFound() throws Exception {
        EventLicenceCheckApi api = configured();
        when(dao(api).find(99)).thenReturn(null);
        assertEquals(404, assertThrows(WebApplicationException.class, () -> api.licenceCheck(99))
                .getResponse().getStatus());
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
    private static EventLicenceCheckApi configured() throws Exception {
        EventLicenceCheckApi api = new EventLicenceCheckApi();
        inject(api, "eventDao", mock(EventDao.class)); inject(api, "playerSubscriptionDao", mock(PlayerSubscriptionDao.class));
        inject(api, "luceneDb", mock(LuceneDb.class)); inject(api, "find", mock(Find.class)); return api;
    }
    private static EventDao dao(EventLicenceCheckApi a) throws Exception { return get(a, "eventDao", EventDao.class); }
    private static PlayerSubscriptionDao subs(EventLicenceCheckApi a) throws Exception { return get(a, "playerSubscriptionDao", PlayerSubscriptionDao.class); }
    private static LuceneDb lucene(EventLicenceCheckApi a) throws Exception { return get(a, "luceneDb", LuceneDb.class); }
    private static Find find(EventLicenceCheckApi a) throws Exception { return get(a, "find", Find.class); }
    @SuppressWarnings("unchecked")
    private static <T> T get(Object o, String n, Class<T> t) throws Exception { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return (T)f.get(o); }
    private static void inject(Object o, String n, Object v) throws Exception { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); f.set(o,v); }
}
