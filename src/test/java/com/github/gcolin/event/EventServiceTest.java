package com.github.gcolin.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.player.Find;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.platform.TestDb;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private Connection connection;
    private EventDao eventService;
    private EventOptionDao eventOptionDao;
    private Find finder;
    private EventGroupDao eventGroupService;
    private PlayerSubscriptionDao playerSubscriptionDao;
    private TestContext testContext;

    @BeforeEach
    void setUp() {
        connection = TestDb.newConnection();
        eventOptionDao = mock(EventOptionDao.class);
        finder = mock(Find.class);
        eventGroupService = mock(EventGroupDao.class);
        playerSubscriptionDao = mock(PlayerSubscriptionDao.class);
        testContext = TestContext.open(connection);
        eventService = TestContext.createDao(EventDao.class, connection);
        eventService.setFinder(finder);
        eventService.setEventGroupDao(() -> eventGroupService);
        eventService.setEventOptionDao(() -> eventOptionDao);
        eventService.setPlayerSubscriptionDao(() -> playerSubscriptionDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        testContext.close();
        connection.close();
    }

    private Event newEvent(String name, EventStatus status, EventGroup group) {
        Event event = new Event();
        event.setName(name);
        event.setStatus(status);
        event.setEventType(EventType.STANDARD);
        event.setPriceCents(1000L);
        event.setYoungPriceCents(500L);
        event.setStartDate(java.time.LocalDate.of(2030, 1, 1).atStartOfDay());
        event.setEndDate(java.time.LocalDate.of(2030, 1, 2).atStartOfDay());
        event.setEventGroup(group);
        eventService.persist(event);
        return event;
    }

    @Test
    void testFindByStatus() {
        Event active = newEvent("Chess Tournament 2024", EventStatus.ACTIVE, null);
        newEvent("Closed one", EventStatus.COMPLETED, null);

        List<Event> result = eventService.findByStatus(EventStatus.ACTIVE);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(active.getId(), result.get(0).getId());
        Assertions.assertEquals("Chess Tournament 2024", result.get(0).getName());
        Assertions.assertEquals(EventStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void testFindByStatusWithEventGroup() {
        EventGroup eventGroup = new EventGroup();
        eventGroup.setName("Group");
        eventGroup.setShortname("group");
        new EventGroupDao().persist(eventGroup);

        Event inGroup = newEvent("Group Tournament", EventStatus.ACTIVE, eventGroup);
        newEvent("No group", EventStatus.ACTIVE, null);

        List<Event> result = eventService.findByStatus(EventStatus.ACTIVE, eventGroup);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(inGroup.getId(), result.get(0).getId());
        Assertions.assertEquals(eventGroup.getId(), result.get(0).getEventGroup().getId());
    }

    @Test
    void testSaveEvent() {
        Integer id = eventService.saveEvent(
                null,
                "New Tournament",
                "2024-06-01",
                "2024-06-03",
                "ACTIVE",
                "BLITZ",
                "15.0",
                "10.0",
                null,
                null,
                "7",
                "90",
                "Haley",
                null);

        Assertions.assertNotNull(id);
        Event saved = eventService.find(id);
        Assertions.assertEquals("New Tournament", saved.getName());
        Assertions.assertEquals(EventType.BLITZ, saved.getEventType());
        Assertions.assertEquals(1500L, saved.getPriceCents());
        verify(eventOptionDao).setOption(id, EventOptionType.ROUNDS, "7");
        verify(eventOptionDao).setOption(id, EventOptionType.CADENCE, "90");
        verify(eventOptionDao).setOption(id, EventOptionType.PAIRING, "Haley");
        verify(eventOptionDao).setOption(id, EventOptionType.MAX_SUBSCRIPTIONS, "");
    }

    @Test
    void fillNbSubscriptionsShouldUseBatchCount() {
        PlayerSubscriptionDao subscriptionDao = mock(PlayerSubscriptionDao.class);
        eventService.setPlayerSubscriptionDao(() -> subscriptionDao);

        Event event1 = new Event();
        event1.setId(1);
        Event event2 = new Event();
        event2.setId(2);
        Event event3 = new Event();
        event3.setId(3);

        when(subscriptionDao.countByEventIds(List.of(1, 2, 3))).thenReturn(Map.of(1, 5L, 2, 10L));

        eventService.fillNbSubscriptions(List.of(event1, event2, event3));

        Assertions.assertEquals(5, event1.getNbSubscriptions());
        Assertions.assertEquals(10, event2.getNbSubscriptions());
        Assertions.assertEquals(0, event3.getNbSubscriptions());
        Assertions.assertTrue(event1.getSubscriptions().isEmpty());
    }

    @Test
    void testBuildCache() {
        Event event = newEvent("Test Event", EventStatus.ACTIVE, null);
        event.setSubscriptions(Collections.emptyList());

        PlayerSubscription sub = new PlayerSubscription();
        sub.setNrFfe("123");
        sub.setId(1);
        sub.setStatus(PlayerSubscriptionStatus.PAID);

        event.setSubscriptions(List.of(sub));

        IPlayer player = mock(IPlayer.class);
        when(player.getNrffe()).thenReturn("123");
        when(player.getName()).thenReturn("Doe");
        when(player.getFirstname()).thenReturn("John");
        when(player.getRating()).thenReturn("1500");

        when(finder.player("123", EventType.STANDARD)).thenReturn(player);

        eventService.updateDescription(event.getId(), "**Test** description");
        when(playerSubscriptionDao.findByEvent(any(Event.class))).thenReturn(List.of(sub));

        EventCache cache = eventService.buildCache(event.getId());

        Assertions.assertNotNull(cache);
        Assertions.assertEquals(event.getId(), cache.event.getId());
        Assertions.assertEquals(1, cache.players.size());
        DisplayPlayer dp = cache.players.get(0);
        Assertions.assertEquals("Doe", dp.getName());
        Assertions.assertEquals("John", dp.getFirstname());
        Assertions.assertEquals("1500", dp.getRating());
        Assertions.assertTrue(cache.missingPlayerCodes.isEmpty());
        Assertions.assertTrue(cache.eventInfo.contains("<strong>Test</strong>"));
        Assertions.assertTrue(cache.event.getSubscriptions().isEmpty());
    }

    @Test
    void buildCacheShouldCollectMissingPlayerCodes() {
        Event event = newEvent("Missing codes", EventStatus.ACTIVE, null);

        PlayerSubscription found = new PlayerSubscription();
        found.setId(1);
        found.setNrFfe("123");
        found.setStatus(PlayerSubscriptionStatus.PAID);

        PlayerSubscription missing = new PlayerSubscription();
        missing.setId(2);
        missing.setNrFfe("MISSING");
        missing.setStatus(PlayerSubscriptionStatus.PAID);

        event.setSubscriptions(List.of(found, missing));

        IPlayer player = mock(IPlayer.class);
        when(player.getNrffe()).thenReturn("123");
        when(player.getName()).thenReturn("Doe");
        when(player.getFirstname()).thenReturn("John");
        when(player.getRating()).thenReturn("1500");

        when(finder.player("123", EventType.STANDARD)).thenReturn(player);
        when(finder.player("MISSING", EventType.STANDARD)).thenReturn(null);
        when(playerSubscriptionDao.findByEvent(any(Event.class))).thenReturn(List.of(found, missing));

        EventCache cache = eventService.buildCache(event.getId());

        // Missing FFE codes are still exported as placeholders and listed separately.
        Assertions.assertEquals(2, cache.players.size());
        Assertions.assertEquals("Doe", cache.players.get(0).getName());
        Assertions.assertEquals("MISSING", cache.players.get(1).getName());
        Assertions.assertEquals("MISSING", cache.players.get(1).getNrffe());
        Assertions.assertEquals(List.of("MISSING"), cache.missingPlayerCodes);
    }
}
