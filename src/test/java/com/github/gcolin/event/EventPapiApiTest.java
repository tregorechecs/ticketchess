package com.github.gcolin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.github.gcolin.event.Event;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.event.PapiService;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.event.EventCache;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doThrow;
import java.nio.file.Path;

class EventPapiApiTest {

    @Test
    void papiShouldReturnFileAsStreamingResponse() throws Exception {
        EventPapiApi api = new EventPapiApi();
        EventDao eventDao = mock(EventDao.class);
        PapiService papiService = mock(PapiService.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Rapid 2024");

        EventCache cache = new EventCache();
        cache.event = event;
        cache.players = List.of();

        File tempFile = File.createTempFile("test", ".papi");
        tempFile.deleteOnExit();

        when(eventDao.buildCache(1)).thenReturn(cache);
        when(papiService.generatePapiFile(event, cache.players)).thenReturn(tempFile);

        inject(api, "eventService", eventDao);
        inject(api, "papiService", papiService);

        Response response = api.papi(1);

        assertEquals(200, response.getStatus());
        assertEquals("application/x-msaccess", response.getMediaType().toString());
    }

    @Test
    void papiShouldEncodeEventNameInFilename() throws Exception {
        EventPapiApi api = new EventPapiApi();
        EventDao eventDao = mock(EventDao.class);
        PapiService papiService = mock(PapiService.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Tournoi 2024-05 Blitz!");

        EventCache cache = new EventCache();
        cache.event = event;
        cache.players = List.of();

        File tempFile = File.createTempFile("test", ".papi");
        tempFile.deleteOnExit();

        when(eventDao.buildCache(1)).thenReturn(cache);
        when(papiService.generatePapiFile(event, cache.players)).thenReturn(tempFile);

        inject(api, "eventService", eventDao);
        inject(api, "papiService", papiService);

        Response response = api.papi(1);

        assertEquals(200, response.getStatus());
    }

    @Test
    void papiShouldExcludeCancelledPlayers() throws Exception {
        EventPapiApi api = new EventPapiApi();
        EventDao eventDao = mock(EventDao.class);
        PapiService papiService = mock(PapiService.class);

        Event event = new Event();
        event.setId(1);
        event.setName("Rapid 2024");

        DisplayPlayer active = new DisplayPlayer();
        active.setStatus(PlayerSubscriptionStatus.PAID);
        DisplayPlayer cancelled = new DisplayPlayer();
        cancelled.setStatus(PlayerSubscriptionStatus.CANCELLED);

        EventCache cache = new EventCache();
        cache.event = event;
        cache.players = List.of(active, cancelled);

        File tempFile = File.createTempFile("test", ".papi");
        tempFile.deleteOnExit();

        when(eventDao.buildCache(1)).thenReturn(cache);
        when(papiService.generatePapiFile(event, List.of(active))).thenReturn(tempFile);

        inject(api, "eventService", eventDao);
        inject(api, "papiService", papiService);

        Response response = api.papi(1);

        assertEquals(200, response.getStatus());
        ArgumentCaptor<List<DisplayPlayer>> captor = ArgumentCaptor.forClass(List.class);
        verify(papiService).generatePapiFile(eq(event), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertTrue(captor.getValue().contains(active));
    }

    @Test
    void uploadToFfeCoversMissingCredentialsSuccessAndIo() throws Exception {
        EventPapiApi api = new EventPapiApi();
        EventDao events = mock(EventDao.class); PapiService papi = mock(PapiService.class);
        PapiUlploadService upload = mock(PapiUlploadService.class); EventOptionDao options = mock(EventOptionDao.class);
        Event event = new Event(); event.setId(1); event.setName("Test");
        EventCache cache = new EventCache(); cache.event = event; cache.players = List.of();
        when(events.buildCache(1)).thenReturn(cache);
        inject(api, "eventService", events); inject(api, "papiService", papi);
        inject(api, "papiUlploadService", upload); inject(api, "eventOptionService", options);
        assertEquals(400, api.uploadToFfe(1).getStatus());
        EventOption id = new EventOption(); id.setValue("id");
        EventOption pass = new EventOption(); pass.setValue("pass");
        when(options.findByEventIdAndOptionType(eq(1), eq(EventOptionType.FFE_ID))).thenReturn(id);
        when(options.findByEventIdAndOptionType(eq(1), eq(EventOptionType.FFE_PASSWORD))).thenReturn(pass);
        File file = File.createTempFile("papi", ".tmp"); file.deleteOnExit();
        when(papi.generatePapiFile(any(), any())).thenReturn(file);
        assertEquals(200, api.uploadToFfe(1).getStatus());
        doThrow(new java.io.IOException("network")).when(upload).upload(eq("id"), eq("pass"), any(Path.class));
        assertEquals(502, api.uploadToFfe(1).getStatus());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
