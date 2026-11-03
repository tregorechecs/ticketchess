package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.payment.RibService;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import org.junit.jupiter.api.*;

class AdminApiExtraTest {
    @AfterEach void close() { RequestContext.close(); }
    @Test void cacheRedirectAndRibDelete() throws Exception {
        AdminApi api = new AdminApi(); Caches caches = new Caches(); UriInfo uri = uri();
        inject(api, "caches", caches); inject(api, "uriInfo", uri);
        assertEquals(303, api.clearCache().getStatus()); assertEquals(303, api.ribRedirect().getStatus());
        RibService rib = mock(RibService.class); Config config = mock(Config.class);
        inject(api, "ribService", rib); inject(api, "config", config);
        assertEquals(200, api.deleteRib().getStatus()); verify(rib).delete(); verify(config).updateProperties(any());
        doThrow(new IOException()).when(rib).delete();
        assertEquals(200, api.deleteRib().getStatus());
    }
    @Test void uploadAndBackgroundBranches() throws Exception {
        AdminApi api = new AdminApi(); UriInfo uri = uri(); Config config = mock(Config.class);
        LogoService logo = mock(LogoService.class); SignatureService sig = mock(SignatureService.class);
        BackgroundService bg = mock(BackgroundService.class);
        inject(api,"uriInfo",uri); inject(api,"config",config); inject(api,"logoService",logo);
        inject(api,"signatureService",sig); inject(api,"backgroundService",bg);
        Organisation org = new Organisation(); org.setId(1);
        RequestContext.openForTest((EntityManager)null); RequestContext.require().setOrganisation(org);
        assertEquals(200, api.uploadLogo(new ByteArrayInputStream(new byte[0])).getStatus());
        assertEquals(200, api.uploadSignature(new ByteArrayInputStream(new byte[0])).getStatus());
        assertEquals(200, api.uploadBackground(new ByteArrayInputStream(new byte[0])).getStatus());
        doThrow(new IOException()).when(bg).delete(); assertEquals(200, api.deleteBackground().getStatus());
    }
    private static UriInfo uri() {
        UriInfo i=mock(UriInfo.class); when(i.getBaseUriBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/"));
        when(i.getRequestUri()).thenReturn(URI.create("http://localhost/admin/rib?a=b")); return i;
    }
    private static void inject(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
}
