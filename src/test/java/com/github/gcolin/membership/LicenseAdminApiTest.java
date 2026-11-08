package com.github.gcolin.membership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class LicenseAdminApiTest {
    @Test void pagesAndSaveBranches() throws Exception {
        LicenseAdminApi api = new LicenseAdminApi();
        LicenseDao dao = mock(LicenseDao.class);
        LicensePriceDao prices = mock(LicensePriceDao.class);
        UriInfo uri = uri();
        inject(api, "licenseDao", dao); inject(api, "licensePriceDao", prices); inject(api, "uriInfo", uri);
        License license = new License("Annual"); license.setId(2);
        when(dao.all()).thenReturn(List.of(license)); when(dao.find(2)).thenReturn(license);
        when(prices.all()).thenReturn(List.of());
        assertEquals("membership/licenseAdmin.mustache", api.list().getTemplate());
        assertEquals("membership/licenseEdit.mustache", api.newLicense().getTemplate());
        assertEquals("membership/licenseEdit.mustache", api.edit(2).getTemplate());
        assertEquals(303, api.save(null, "New", "", null).getStatus());
        verify(dao).persist(any(License.class));
        assertEquals(303, api.save(2, "Changed", "INVALID", null).getStatus());
        verify(dao).merge(license);
        LicensePrice price = new LicensePrice("A", 100, license); price.setId(4);
        when(prices.all()).thenReturn(List.of(price));
        assertEquals("membership/licensePriceAdmin.mustache", api.listPrices(2).getTemplate());
        assertEquals("membership/licensePriceEdit.mustache", api.newPrice(2).getTemplate());
        when(prices.find(4)).thenReturn(price);
        assertEquals("membership/licensePriceEdit.mustache", api.editPrice(2, 4).getTemplate());
        assertEquals(303, api.savePrice(null, 2, "A", "12.34", null).getStatus());
        verify(prices).persist(any(LicensePrice.class));
        assertEquals(303, api.savePrice(4, 2, "B", "2.50", null).getStatus());
        verify(prices).merge(price);
    }

    @Test void deleteBranches() throws Exception {
        LicenseAdminApi api = new LicenseAdminApi();
        LicenseDao dao = mock(LicenseDao.class); LicensePriceDao prices = mock(LicensePriceDao.class);
        inject(api, "licenseDao", dao); inject(api, "licensePriceDao", prices); inject(api, "uriInfo", uri());
        License l = new License("x"); l.setId(3); LicensePrice p = new LicensePrice("A", 1, l);
        when(prices.all()).thenReturn(List.of(p));
        assertEquals(303, api.save(3, null, null, "true").getStatus());
        verify(prices).remove(p); verify(dao).remove(3);
        assertEquals(303, api.savePrice(4, 3, "A", "1", "true").getStatus());
        verify(prices).remove(4);
    }

    private static UriInfo uri() {
        UriInfo info = mock(UriInfo.class); UriBuilder b = UriBuilder.fromUri("http://localhost/");
        when(info.getBaseUriBuilder()).thenReturn(b); return info;
    }
    private static void inject(Object o, String n, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); f.set(o, v);
    }
}
