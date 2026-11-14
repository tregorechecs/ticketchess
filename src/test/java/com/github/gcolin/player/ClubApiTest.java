package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClubApiTest {
    @Test
    void compositionCountsGenderLicencesAndCategories() throws Exception {
        ClubApi api = new ClubApi();
        LuceneDb db = mock(LuceneDb.class);
        when(db.searchJoueursByClub(42))
                .thenReturn(List.of(
                        player("M", "A", "M", "N"),
                        player("M", "B", "M", "A"),
                        player("F", "A", "F", "A"),
                        player("F", "B", "F", "B"),
                        player("F", "C", "F", "B"),
                        player("F", "D", "F", "N"),
                        player("F", "E", "U10F", "B")));
        try (TestContext tc = TestContext.openApp(app -> app.setLuceneDb(db))) {
            assertEquals(
                    "M: 1, F: 4 total:5 licA:2 licB: 3\n "
                            + "{F=1, M=1}\n "
                            + "{F=2, U10F=1}",
                    api.composition(42));
        }
    }

    private static Player player(String name, String first, String category, String aff) {
        Player p = new Player();
        p.setName(name);
        p.setFirstname(first);
        p.setCategory(category);
        p.setAffType(aff);
        p.setNrffe(aff.equals("A") ? "Z-A" : "B");
        return p;
    }
}
