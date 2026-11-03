package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;

import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.MustacheHtml;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.net.URI;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class DatabaseApiTest {

    @Test
    void pageShouldReturnFfeSettingsInModel() throws Exception {
        DatabaseApi api = new DatabaseApi();
        Config config = mock(Config.class);
        when(config.getProperty("ffe.excludeAffTypeN", "false")).thenReturn("true");

        inject(api, "luceneDb", mock(LuceneDb.class));
        inject(api, "config", config);

        MustacheHtml html = api.page(null);

        assertEquals("player/database.mustache", html.getTemplate());
        Map<String, Object> model = html.getModel();
        assertEquals(true, model.get("excludeAffTypeN"));
        assertEquals(null, model.get("status"));
    }

    @Test
    void getFfeInfoShouldReturnFileInfo() throws Exception {
        DatabaseApi api = new DatabaseApi();
        LuceneDb luceneDb = mock(LuceneDb.class);

        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.getAbsolutePath()).thenReturn("/path/to/Data.mdb");
        when(mockFile.lastModified()).thenReturn(1000000L);
        when(mockFile.length()).thenReturn(1024L);

        when(luceneDb.getMdbFile()).thenReturn(mockFile);

        inject(api, "luceneDb", luceneDb);

        String result = api.getFfeInfo();

        assertTrue(result.contains("/path/to/Data.mdb"));
        assertTrue(result.contains("1970-01-01"));
    }

    @Test
    void getFfeInfoShouldReturnNotFoundWhenFileNull() throws Exception {
        DatabaseApi api = new DatabaseApi();
        LuceneDb luceneDb = mock(LuceneDb.class);

        when(luceneDb.getMdbFile()).thenReturn(null);

        inject(api, "luceneDb", luceneDb);

        String result = api.getFfeInfo();

        assertEquals("Data.mdb not found", result);
    }

    @Test
    void getFideInfoShouldReturnFileInfo() throws Exception {
        DatabaseApi api = new DatabaseApi();
        LuceneDb luceneDb = mock(LuceneDb.class);

        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.getAbsolutePath()).thenReturn("/path/to/fide.mdb");
        when(mockFile.lastModified()).thenReturn(2000000L);
        when(mockFile.length()).thenReturn(2048L);

        when(luceneDb.getFideFile()).thenReturn(mockFile);

        inject(api, "luceneDb", luceneDb);

        String result = api.getFideInfo();

        assertTrue(result.contains("/path/to/fide.mdb"));
        assertTrue(result.contains("1970-01-01"));
    }

    @Test
    void settingsDownloadsReloadAndManualPlayers() throws Exception {
        DatabaseApi api = new DatabaseApi(); LuceneDb db = mock(LuceneDb.class); Config config = mock(Config.class);
        UriInfo uri = mock(UriInfo.class); when(uri.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));
        inject(api, "luceneDb", db); inject(api, "config", config); inject(api, "uriInfo", uri);
        assertEquals(303, api.saveFfeSettings("on").getStatus());
        verify(config).updateProperties(org.mockito.ArgumentMatchers.argThat(m -> "true".equals(m.get("ffe.excludeAffTypeN"))));
        doThrow(new IOException("x")).when(db).downloadAndExtractFFeDb();
        assertEquals(500, api.downloadAndExtractFfeDb().getStatus());
        doThrow(new IOException("x")).when(db).downloadFideDB();
        assertEquals(500, api.downloadAndExtractFideDb().getStatus());
        doThrow(new IOException("x")).when(db).load(true);
        assertEquals(500, api.reload().getStatus());
        when(db.getManualPlayers()).thenReturn(java.util.List.of());
        java.io.File f = mock(java.io.File.class); when(f.getAbsolutePath()).thenReturn("/manual.csv");
        when(db.getManualPlayersFile()).thenReturn(f);
        assertEquals("player/databasePlayers.mustache", api.manualPlayersPage("ok").getTemplate());
        assertEquals(303, api.addManualPlayer("n","f","Name","First","2000","A","FRA","Club","1","2","3","GM").getStatus());
        verify(db).addManualPlayer(any());
        assertEquals(303, api.deleteManualPlayer("x").getStatus());
        when(db.searchJoueurByNrffe("n")).thenReturn(null);
        when(db.searchJoueurByFide("f")).thenReturn(new Player());
        assertNotNull(api.prefillManualPlayer(" n ", " f "));
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
