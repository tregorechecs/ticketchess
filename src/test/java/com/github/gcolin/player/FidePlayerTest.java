package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FidePlayerTest {
    @Test void allPropertiesRoundTrip() {
        FidePlayer p = new FidePlayer();
        p.setFideid(1); p.setName("N"); p.setCountry("FRA"); p.setSex("M");
        p.setTitle("GM"); p.setW_title("WGM"); p.setO_title("FM"); p.setFoa_title("FA");
        p.setRating(2000); p.setGames(3); p.setK(20); p.setRapid_rating(1900);
        p.setRapid_games(4); p.setRapid_k(10); p.setBlitz_rating(1800);
        p.setBlitz_games(5); p.setBlitz_k(8); p.setBirthday(1990); p.setFlag("x");
        assertEquals(1, p.getFideid()); assertEquals("N", p.getName()); assertEquals("FRA", p.getCountry());
        assertEquals("M", p.getSex()); assertEquals("GM", p.getTitle()); assertEquals("WGM", p.getW_title());
        assertEquals("FM", p.getO_title()); assertEquals("FA", p.getFoa_title()); assertEquals(2000, p.getRating());
        assertEquals(3, p.getGames()); assertEquals(20, p.getK()); assertEquals(1900, p.getRapid_rating());
        assertEquals(4, p.getRapid_games()); assertEquals(10, p.getRapid_k()); assertEquals(1800, p.getBlitz_rating());
        assertEquals(5, p.getBlitz_games()); assertEquals(8, p.getBlitz_k()); assertEquals(1990, p.getBirthday());
        assertEquals("x", p.getFlag());
    }
}
