package com.github.gcolin.platform;

import com.github.gcolin.player.Find;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;

public final class TestContext implements AutoCloseable {

    private boolean clearAppContext;

    private TestContext() {}

    public static TestContext open(Connection connection) {
        RequestContext.openForTest(connection);
        return new TestContext();
    }

    public static TestContext open(Find find) {
        RequestContext.openForTest(find);
        return new TestContext();
    }

    public static TestContext open(com.github.gcolin.auth.LoggedUser loggedUser) {
        RequestContext.openForTest(loggedUser);
        return new TestContext();
    }

    public static TestContext open(
            com.github.gcolin.auth.LoggedUser loggedUser,
            com.github.gcolin.auth.ActiveLoggedUsers activeLoggedUsers) {
        AppContext.initForTest(activeLoggedUsers);
        TestContext ctx = open(loggedUser);
        ctx.clearAppContext = true;
        return ctx;
    }

    public static TestContext open(Connection connection, Find find) {
        RequestContext.openForTest(connection, find);
        return new TestContext();
    }

    public static TestContext open(HttpServletRequest request, Connection connection) {
        RequestContext.openForTest(request, connection);
        return new TestContext();
    }

    public RequestContext ctx() {
        return RequestContext.require();
    }

    public static <T extends AbstractDao<?>> T createDao(Class<T> type, Connection connection) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new IllegalStateException("Cannot create dao " + type.getName(), e);
        }
    }

    @Override
    public void close() {
        RequestContext.close();
        if (clearAppContext) {
            AppContext.clearTestInstance();
        }
    }
}
