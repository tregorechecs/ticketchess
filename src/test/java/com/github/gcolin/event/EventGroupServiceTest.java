package com.github.gcolin.event;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.platform.TestDb;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventGroupServiceTest {

    private Connection connection;
    private EventGroupDao eventGroupService;
    private TestContext testContext;

    @BeforeEach
    void setUp() {
        connection = TestDb.newConnection();
        testContext = TestContext.open(connection);
        eventGroupService = TestContext.createDao(EventGroupDao.class, connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        testContext.close();
        connection.close();
    }

    private EventGroup persistGroup(String name, String shortname) {
        EventGroup group = new EventGroup();
        group.setName(name);
        group.setShortname(shortname);
        eventGroupService.persist(group);
        return group;
    }

    @Test
    void testFindByShortname() {
        EventGroup saved = persistGroup("Chess 2024", "chess2024");

        EventGroup result = eventGroupService.findByShortname("chess2024");

        Assertions.assertNotNull(result);
        Assertions.assertEquals(saved.getId(), result.getId());
        Assertions.assertEquals("Chess 2024", result.getName());
    }

    @Test
    void testFindByShortname_NotFound() {
        persistGroup("Chess 2024", "chess2024");

        Assertions.assertNull(eventGroupService.findByShortname("unknown"));
    }

    @Test
    void testFindDetachedForEdit() {
        EventGroup saved = persistGroup("Chess 2024", "chess2024");

        EventGroup result = eventGroupService.findDetachedForEdit(saved.getId());

        Assertions.assertNotNull(result);
        Assertions.assertEquals(saved.getId(), result.getId());
        Assertions.assertEquals(List.of(), result.getEvents());
    }
}
