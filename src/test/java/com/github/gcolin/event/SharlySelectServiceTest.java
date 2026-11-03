package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SharlySelectServiceTest {
    @Test
    void listsCollectionsAndStandaloneEventsSortedByName() {
        EventDao events = mock(EventDao.class);
        EventCollectionOptionDao options = mock(EventCollectionOptionDao.class);
        EventCollection c = collection(2, "Collection");
        Event first = event(10, "Z event", c);
        Event second = event(11, "A event", c);
        Event standalone = event(12, "B event", null);
        when(events.findAllByStatus(EventStatus.ACTIVE)).thenReturn(List.of(first, second, standalone));
        when(options.findOptionValue(2, EventCollectionOptionType.CHESS_EVENT_ID)).thenReturn(" slug ");
        List<SharlySelectItem> result = new SharlySelectService(events, options).listOpenItems();
        assertEquals(2, result.size());
        assertEquals("B event", result.get(0).getName());
        assertEquals("Collection", result.get(1).getName());
        assertEquals("slug", result.get(1).getChessEventId());
        assertEquals(2, result.get(1).getTournamentCount());
    }

    @Test
    void fallsBackToCollectionIdAndResolvesValidOrInvalidSelections() {
        EventDao events = mock(EventDao.class);
        EventCollectionOptionDao options = mock(EventCollectionOptionDao.class);
        EventCollection c = collection(3, "Cup");
        Event e = event(20, "Open", c);
        when(events.findAllByStatus(EventStatus.ACTIVE)).thenReturn(List.of(e));
        when(options.findOptionValue(3, EventCollectionOptionType.CHESS_EVENT_ID)).thenReturn("  ");
        SharlySelectService service = new SharlySelectService(events, options);
        assertEquals("3", service.listOpenItems().get(0).getChessEventId());
        assertNotNull(service.resolveSelection("collection", 3, null));
        assertNull(service.resolveSelection("EVENT", null, 20));
        assertNull(service.resolveSelection(null, 3, null));
        assertNull(service.resolveSelection("bad", 3, 20));
        assertNull(service.resolveSelection("COLLECTION", 99, null));
    }

    private static EventCollection collection(int id, String name) {
        EventCollection c = new EventCollection(); c.setId(id); c.setName(name); return c;
    }
    private static Event event(int id, String name, EventCollection c) {
        Event e = new Event(); e.setId(id); e.setName(name); e.setStatus(EventStatus.ACTIVE); e.setEventCollection(c); return e;
    }
}
