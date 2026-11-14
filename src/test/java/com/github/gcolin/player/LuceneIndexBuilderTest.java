package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.platform.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceneIndexBuilderTest {

    @TempDir
    Path tempDir;

    private String previousConfigDir;
    private String previousLoadtest;
    private String previousTest;

    @AfterEach
    void tearDown() {
        restore("CONFIG_DIR", previousConfigDir);
        restore("loadtest", previousLoadtest);
        restore("test", previousTest);
    }

    @Test
    void rebuildWritesAtomicIndexReadableOnDisk() throws Exception {
        previousConfigDir = System.getProperty("CONFIG_DIR");
        previousLoadtest = System.getProperty("loadtest");
        previousTest = System.getProperty("test");
        System.setProperty("CONFIG_DIR", tempDir.toAbsolutePath().toString());
        System.setProperty("loadtest", "true");
        System.clearProperty("test");

        Files.createDirectories(tempDir.resolve("shared"));
        Files.createDirectories(tempDir.resolve("lucene"));

        Config config = new Config();
        config.init(null);
        LuceneIndexBuilder.rebuild(config);

        Path indexDir = tempDir.resolve("lucene").resolve("index");
        assertTrue(Files.isDirectory(indexDir));
        assertTrue(!Files.exists(tempDir.resolve("lucene").resolve("index.build")));

        try (FSDirectory directory = FSDirectory.open(indexDir);
                DirectoryReader reader = DirectoryReader.open(directory)) {
            assertTrue(reader.numDocs() > 0);
        }

        LuceneDb db = new LuceneDb();
        db.setConfig(config);
        db.init();
        try {
            db.load(false);
            Player player = db.searchJoueur("X82897");
            assertEquals("COLIN", player.getName());
        } finally {
            db.close();
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
