package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.event.EventDao;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class EventCsvApiTest {

    @Test
    void papiShouldReturnCsvResponseWithCorrectHeaders() throws Exception {
        EventCsvApi api = new EventCsvApi();
        EventDao eventDao = mock(EventDao.class);

        String csvContent = "id,name\n1,Alice\n";
        when(eventDao.buildCsv(42)).thenReturn(csvContent);

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
        })) {

            Response response = api.papi(42);

            assertEquals(200, response.getStatus());
            assertEquals("text/csv", response.getMediaType().toString());
            assertEquals("attachment; filename=\"42.csv\"", response.getHeaderString("Content-Disposition"));
            assertEquals(csvContent, response.getEntity());
        }
    }

    @Test
    void papiShouldUseEventIdInFilename() throws Exception {
        EventCsvApi api = new EventCsvApi();
        EventDao eventDao = mock(EventDao.class);

        when(eventDao.buildCsv(7)).thenReturn("data");

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
        })) {

            Response response = api.papi(7);

            assertEquals("attachment; filename=\"7.csv\"", response.getHeaderString("Content-Disposition"));
        }
    }

    @Test
    void papiShouldReturnEmptyCsvBodyWhenDaoReturnsEmptyString() throws Exception {
        EventCsvApi api = new EventCsvApi();
        EventDao eventDao = mock(EventDao.class);

        when(eventDao.buildCsv(99)).thenReturn("");

        try (TestContext tc = TestContext.openRequest(ctx -> {
            ctx.setEventDao(eventDao);
        })) {

            Response response = api.papi(99);

            assertEquals(200, response.getStatus());
            assertTrue(((String) response.getEntity()).isEmpty());
        }
    }
}
