package com.github.gcolin.platform;

import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RequestContextReadOnlyTest {

    @AfterEach
    void tearDown() {
        RequestContext.close();
    }

    @Test
    void commitReadAndClearCommitsReadOnlyConnection() throws Exception {
        Connection connection = Mockito.mock(Connection.class);

        RequestContext.openForTestReadOnly(connection);
        Assertions.assertTrue(RequestContext.isReadOnly());

        RequestContext.commitReadAndClear();

        Mockito.verify(connection).commit();
    }

    @Test
    void commitReadAndClearIgnoresWritableContext() throws Exception {
        Connection connection = Mockito.mock(Connection.class);

        RequestContext.openForTest(connection);
        Assertions.assertFalse(RequestContext.isReadOnly());

        RequestContext.commitReadAndClear();

        Mockito.verify(connection, Mockito.never()).commit();
    }

    @Test
    void daoFactoryDoesNotTouchConnectionEagerly() throws Exception {
        Connection connection = Mockito.mock(Connection.class);

        RequestContext.openForTestReadOnly(connection);
        RequestContext.require().licenseDao();

        Mockito.verifyNoInteractions(connection);
    }
}
