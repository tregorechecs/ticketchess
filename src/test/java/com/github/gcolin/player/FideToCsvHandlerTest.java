package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Test;

class FideToCsvHandlerTest {
    @Test void convertsPlayerXmlToCsv() throws Exception {
        StringWriter output = new StringWriter();
        FideToCsvHandler handler = new FideToCsvHandler(new BufferedWriter(output));
        SAXParserFactory.newInstance().newSAXParser().parse(
            new ByteArrayInputStream("<players><player><fideid>42</fideid><name>A B</name><sex>M</sex><birthday>1990</birthday><country>FRA</country><title>GM</title><rating>2000</rating><rapid_rating>1900</rapid_rating><blitz_rating>1800</blitz_rating></player></players>"
                .getBytes(StandardCharsets.UTF_8)), handler);
        handler.writer.flush();
        assertTrue(output.toString().contains("fideCode;name;sex;birth;federation;fideTitre;eloStd;eloRapide;eloBlitz"));
        assertTrue(output.toString().contains("42;A B;M;1990;FRA;GM;2000;1900;1800"));
    }
}
