package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.event.Event;
import com.github.gcolin.registration.PlayerSubscription;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

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
            DbInit dbInit = new DbInit(persistence.getEmf());
            dbInit.initLicenses();
            dbInit.initClubSeason();
            dbInit.initMembershipOptions();
            dbInit.initEvents();

            EntityManager em = persistence.getEmf().createEntityManager();
            try {
                Event deskEvent = em.find(Event.class, 3);
                assertEquals("Sample Event 3", deskEvent.getName());

                List<PlayerSubscription> subs = em.createQuery(
                                "SELECT s FROM PlayerSubscription s WHERE s.event.id = 3", PlayerSubscription.class)
                        .getResultList();
                assertEquals(10, subs.size());
                assertTrue(subs.stream().allMatch(s -> s.getNrFfe() != null && s.getNrFfe().startsWith("@")));
            } finally {
                em.close();
            }
        } finally {
            persistence.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testInitEventsDoesNothingWhenDatabaseAlreadyInitialized() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        DbInit dbInit = new DbInit(emf);
        EntityManager em = Mockito.mock(EntityManager.class);
        TypedQuery<Long> query = Mockito.mock(TypedQuery.class);
        EntityTransaction tx = Mockito.mock(EntityTransaction.class);

        Mockito.when(emf.createEntityManager()).thenReturn(em);
        Mockito.when(em.createQuery("select count(e) from Event e", Long.class)).thenReturn(query);
        Mockito.when(query.getSingleResult()).thenReturn(1L);
        Mockito.when(em.getTransaction()).thenReturn(tx);

        Assertions.assertDoesNotThrow(dbInit::initEvents);

        Mockito.verify(tx, Mockito.never()).begin();
        Mockito.verify(tx, Mockito.never()).commit();
        Mockito.verify(em, Mockito.never()).persist(ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testInitEventsInitializesWhenDatabaseIsEmpty() {
        EntityManagerFactory emf = Mockito.mock(EntityManagerFactory.class);
        DbInit dbInit = new DbInit(emf);
        EntityManager em = Mockito.mock(EntityManager.class);
        TypedQuery<Long> query = Mockito.mock(TypedQuery.class);
        EntityTransaction tx = Mockito.mock(EntityTransaction.class);

        Mockito.when(emf.createEntityManager()).thenReturn(em);
        Mockito.when(em.createQuery("select count(e) from Event e", Long.class)).thenReturn(query);
        Mockito.when(query.getSingleResult()).thenReturn(0L);
        Mockito.when(em.getTransaction()).thenReturn(tx);

        Assertions.assertDoesNotThrow(dbInit::initEvents);

        Mockito.verify(tx).begin();
        Mockito.verify(tx).commit();
        Mockito.verify(em, Mockito.times(36)).persist(ArgumentMatchers.any());
    }
}
