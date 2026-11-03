package com.github.gcolin.player;

import com.github.gcolin.platform.Config;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One lazily created {@link LuceneDb} per organisation; FFE/FIDE source files are shared. */
public class LuceneDbRegistry {

    private static final Logger logger = LoggerFactory.getLogger(LuceneDbRegistry.class);

    private final Config config;
    private final Map<Integer, LuceneDb> databases = new ConcurrentHashMap<>();

    public LuceneDbRegistry(Config config) {
        this.config = config;
    }

    public LuceneDb get(int orgId) {
        return databases.computeIfAbsent(orgId, id -> {
            LuceneDb db = new LuceneDb();
            db.setConfig(config);
            db.init(id);
            return db;
        });
    }

    public void closeAll() {
        for (LuceneDb db : databases.values()) {
            try {
                db.close();
            } catch (IOException e) {
                logger.warn("Cannot close lucene index", e);
            }
        }
        databases.clear();
    }
}
