package com.github.gcolin.player;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.platform.TestDb;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomPlayerServiceTest {

    private Connection connection;
    private CustomPlayerDao customPlayerService;
    private TestContext testContext;

    @BeforeEach
    void setUp() {
        connection = TestDb.newConnection();
        testContext = TestContext.open(connection);
        customPlayerService = TestContext.createDao(CustomPlayerDao.class, connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        testContext.close();
        connection.close();
    }

    @Test
    void testFindByCreationUser() {
        CustomPlayer mine = new CustomPlayer();
        mine.setCreationUser("bob@example.com");
        mine.setName("Doe");
        mine.setFirstname("John");
        customPlayerService.persist(mine);

        CustomPlayer other = new CustomPlayer();
        other.setCreationUser("alice@example.com");
        other.setName("Smith");
        other.setFirstname("Jane");
        customPlayerService.persist(other);

        List<CustomPlayer> result = customPlayerService.findByCreationUser("bob@example.com");

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(mine.getId(), result.get(0).getId());
        Assertions.assertEquals("Doe", result.get(0).getName());
    }
}
