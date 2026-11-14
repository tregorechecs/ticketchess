package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.Messages;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatisticsMustacheEnricherTest {

    @Test
    void enrichDashboardShouldFlattenTopEventsForMustache() {
        Event event = new Event();
        event.setId(7);
        event.setName("Open A");
        event.setStartDate(LocalDateTime.of(2026, 3, 1, 9, 0));
        event.setStatus(EventStatus.ACTIVE);

        Map<String, Object> model = new HashMap<>();
        model.put("totalPayments", 1000L);
        model.put("unpaidPlayerSubscriptions", 200L);
        model.put("closestEvents", List.of(event));
        model.put("topEvents", java.util.Collections.singletonList(new Object[] {event, 12L}));

        StatisticsMustacheEnricher.enrichDashboard(model, new Messages(Locale.FRANCE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topRows = (List<Map<String, Object>>) model.get("topEvents");
        assertEquals(1, topRows.size());
        Map<String, Object> row = topRows.get(0);
        assertEquals(7, row.get("id"));
        assertEquals("Open A", row.get("name"));
        assertEquals(12L, row.get("participantCount"));
        assertEquals("ACTIVE", row.get("statusName"));
        assertTrue((Boolean) row.get("hasStartDate"));
        assertFalse((Boolean) model.get("topEventsEmpty"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> closestRows = (List<Map<String, Object>>) model.get("closestEvents");
        assertEquals(1, closestRows.size());
        assertEquals("Open A", closestRows.get(0).get("name"));
        assertFalse((Boolean) model.get("closestEventsEmpty"));
    }
}
