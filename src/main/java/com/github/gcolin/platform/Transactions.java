package com.github.gcolin.platform;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

public final class Transactions {

    private Transactions() {}

    public static void run(Connection connection, Runnable work) {
        run(connection, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Runs {@code work} in a transaction on {@code connection}. When a transaction is already in progress
     * (auto-commit disabled, as for request-scoped connections) the work simply joins it and the owner
     * commits; otherwise the work is committed here, or rolled back on failure.
     */
    public static <T> T run(Connection connection, Supplier<T> work) {
        boolean ownsTransaction;
        try {
            ownsTransaction = connection.getAutoCommit();
            if (!ownsTransaction) {
                return work.get();
            }
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new PersistenceException("Cannot start transaction", e);
        }
        try {
            T result = work.get();
            connection.commit();
            return result;
        } catch (RuntimeException | SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new PersistenceException("Transaction failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
                // connection may already be closed
            }
        }
    }

    /** Runs {@code work} inside the current request's transaction (see {@link RequestContext}). */
    public static <T> T run(Supplier<T> work) {
        return run(RequestContext.require().connection(), work);
    }
}
