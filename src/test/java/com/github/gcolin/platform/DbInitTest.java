package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbInitTest {

    @Test
    void initEventsShouldSeedDeskPlayersOnEventIdThree(@TempDir Path tempDir) throws Exception {
        Config config = new Config();
        config.getProperties().putAll(java.util.Map.of(
                "db.type", "h2",
                "db.name", "dbinitdesk",
                "db.h2.path", tempDir.toString(),
                "testmode", "true",
                "jwt.key", "01234567890123456789012345678901"));

        PersistenceService persistence = new PersistenceService(config);
        try {
            persistence.init();
            DbInit dbInit = new DbInit(persistence);
            dbInit.initLicenses();
            dbInit.initClubSeason();
            dbInit.initMembershipOptions();
            dbInit.initEvents();

            RequestContext.run(persistence.getDataSource(), () -> {
                OrgScope.disableFilter();
                Event deskEvent = new EventDao().find(3);
                assertEquals("Sample Event 3", deskEvent.getName());

                List<PlayerSubscription> subs = new PlayerSubscriptionDao().findByEvent(deskEvent);
                assertEquals(10, subs.size());
                assertTrue(subs.stream().allMatch(s -> s.getNrFfe() != null && s.getNrFfe().startsWith("@")));
                return null;
            });
        } finally {
            persistence.shutdown();
        }
    }

    private static long countEvents(DataSource dataSource) {
        return RequestContext.run(dataSource, () -> Db.scalarLongOrZero("SELECT COUNT(*) FROM event"));
    }

    @Test
    void testInitEventsDoesNothingWhenDatabaseAlreadyInitialized() {
        DataSource dataSource = TestDb.newDataSource();
        DbInit dbInit = new DbInit(dataSource);
        dbInit.initEvents();
        long afterFirst = countEvents(dataSource);
        assertTrue(afterFirst > 0);

        dbInit.initEvents();

        assertEquals(afterFirst, countEvents(dataSource));
    }

    @Test
    void testInitEventsInitializesWhenDatabaseIsEmpty() {
        DataSource dataSource = TestDb.newDataSource();
        assertEquals(0L, countEvents(dataSource));

        new DbInit(dataSource).initEvents();

        assertTrue(countEvents(dataSource) > 0);
    }
}
