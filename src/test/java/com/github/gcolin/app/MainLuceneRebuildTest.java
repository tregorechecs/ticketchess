package com.github.gcolin.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainLuceneRebuildTest {

    @TempDir
    Path tempDir;

    private String previousConfigDir;
    private String previousLoadtest;

    @AfterEach
    void tearDown() {
        restore("CONFIG_DIR", previousConfigDir);
        restore("loadtest", previousLoadtest);
    }

    @Test
    void rebuildLuceneIndexReturnsZeroAndCreatesIndex() throws Exception {
        previousConfigDir = System.getProperty("CONFIG_DIR");
        previousLoadtest = System.getProperty("loadtest");
        System.setProperty("CONFIG_DIR", tempDir.toAbsolutePath().toString());
        System.setProperty("loadtest", "true");
        Files.createDirectories(tempDir.resolve("shared"));

        assertEquals(0, Main.rebuildLuceneIndex());
        assertTrue(Files.isDirectory(tempDir.resolve("lucene").resolve("index")));
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
