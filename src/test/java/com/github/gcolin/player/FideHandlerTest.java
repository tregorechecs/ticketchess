package com.github.gcolin.player;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParserFactory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.document.Document;

class FideHandlerTest {

    @Test
    void formatFideRatingUses1399ForUnrated() {
        Assertions.assertEquals("1399F", FideHandler.formatFideRating("0"));
        Assertions.assertEquals("1399F", FideHandler.formatFideRating(""));
        Assertions.assertEquals("1399F", FideHandler.formatFideRating(null));
        Assertions.assertEquals("1399F", FideHandler.formatFideRating("   "));
    }

    @Test
    void formatFideRatingKeepsRatedValue() {
        Assertions.assertEquals("1950F", FideHandler.formatFideRating("1950"));
        Assertions.assertEquals("2100F", FideHandler.formatFideRating(" 2100 "));
    }

    @Test
    void parsesForeignPlayerAndSkipsFrenchPlayer() throws Exception {
        IndexWriter writer = mock(IndexWriter.class);
        FideHandler handler = new FideHandler(writer);
        String xml = "<players><player><fideid>7</fideid><name>Jane Doe</name><sex>F</sex>"
                + "<birthday></birthday><country>USA</country><title>WGM</title>"
                + "<rating>0</rating><rapid_rating>1500</rapid_rating><blitz_rating></blitz_rating></player>"
                + "<player><fideid>8</fideid><name>French</name><sex>M</sex><country>FRA</country></player></players>";
        SAXParserFactory.newInstance().newSAXParser().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
        verify(writer).addDocument(any(Document.class));
        verify(writer, times(1)).addDocument(any(Document.class));
    }
}
