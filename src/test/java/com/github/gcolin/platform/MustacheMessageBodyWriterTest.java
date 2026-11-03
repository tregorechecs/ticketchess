package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.MediaType;
import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;

class MustacheMessageBodyWriterTest {

    @Test
    void recognizesOnlyMustacheHtml() {
        MustacheMessageBodyWriter writer = new MustacheMessageBodyWriter();
        Annotation[] annotations = new Annotation[0];
        assertTrue(writer.isWriteable(MustacheHtml.class, null, annotations, MediaType.TEXT_HTML_TYPE));
        assertFalse(writer.isWriteable(String.class, null, annotations, MediaType.TEXT_HTML_TYPE));
    }

    @Test
    void resolvesBrandFromPageOrDefault() {
        Page page = new Page();
        page.setOrgName("  Chess Club  ");
        assertEquals("Chess Club", MustacheMessageBodyWriter.resolveNavbarBrand(page));
        page.setOrgName("");
        page.setTitle("  Event title ");
        assertEquals("Event title", MustacheMessageBodyWriter.resolveNavbarBrand(page));
        page.setTitle("");
        assertEquals("TicketChess", MustacheMessageBodyWriter.resolveNavbarBrand(page));
    }
}
