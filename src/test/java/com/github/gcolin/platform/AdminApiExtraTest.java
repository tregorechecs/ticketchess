package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.payment.RibService;
import java.sql.Connection;
import jakarta.ws.rs.core.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import org.junit.jupiter.api.*;

class AdminApiExtraTest {
    @AfterEach void close() { RequestContext.close(); }
    @Test void cacheRedirectAndRibDelete() throws Exception {
        AdminApi api = new AdminApi(); Caches caches = new Caches(); UriInfo uri = uri();
        RibService rib = mock(RibService.class); Config config = mock(Config.class);
        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(caches);
            app.setRibService(rib);
            app.setConfig(config);
        }, ctx -> ctx.setUriInfo(uri))) {
            assertEquals(303, api.clearCache().getStatus()); assertEquals(303, api.ribRedirect().getStatus());
            assertEquals(200, api.deleteRib().getStatus()); verify(rib).delete(); verify(config).updateProperties(any());
            doThrow(new IOException()).when(rib).delete();
            assertEquals(200, api.deleteRib().getStatus());
        }
    }
    @Test void uploadAndBackgroundBranches() throws Exception {
        AdminApi api = new AdminApi(); UriInfo uri = uri(); Config config = mock(Config.class);
        LogoService logo = mock(LogoService.class); SignatureService sig = mock(SignatureService.class);
        BackgroundService bg = mock(BackgroundService.class);
        try (TestContext tc = TestContext.openApp(app -> {
            app.setConfig(config);
            app.setLogoService(logo);
            app.setSignatureService(sig);
            app.setBackgroundService(bg);
        })) {
            Organisation org = new Organisation(); org.setId(1);
            RequestContext.openForTest((java.sql.Connection) null); RequestContext.require().setOrganisation(org);
            RequestContext.require().setUriInfo(uri);
            assertEquals(200, api.uploadLogo(new ByteArrayInputStream(new byte[0])).getStatus());
            assertEquals(200, api.uploadSignature(new ByteArrayInputStream(new byte[0])).getStatus());
            assertEquals(200, api.uploadBackground(new ByteArrayInputStream(new byte[0])).getStatus());
            doThrow(new IOException()).when(bg).delete(); assertEquals(200, api.deleteBackground().getStatus());
        }
    }
    private static UriInfo uri() {
        UriInfo i=mock(UriInfo.class); when(i.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));
        when(i.getRequestUri()).thenReturn(URI.create("http://localhost/admin/rib?a=b")); return i;
    }
    private static void inject(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
}
