package com.github.gcolin.organisation;

import com.github.gcolin.platform.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-organisation file layout: {@code CONFIG_DIR/orgs/{orgId}} plus {@code CONFIG_DIR/shared}. */
public final class OrganisationFiles {

    private static final Logger logger = LoggerFactory.getLogger(OrganisationFiles.class);
    private static final List<String> LEGACY_ROOT_FILES = List.of(
            "logo.png",
            "logo.jpg",
            "logo.jpeg",
            "logo.webp",
            "signature.png",
            "signature.jpg",
            "signature.jpeg",
            "signature.webp",
            "background.png",
            "background.jpg",
            "background.jpeg",
            "background.webp",
            "rib.pdf",
            "membership-files");
    private static final List<String> LEGACY_SHARED_FILES = List.of("Data.mdb", "players_list_xml.zip");

    private OrganisationFiles() {}

    public static Path orgDir(Config config, int orgId) {
        return Path.of(config.getConfigDir(), "orgs", String.valueOf(orgId));
    }

    public static Path sharedDir(Config config) {
        return Path.of(config.getConfigDir(), "shared");
    }

    /** Directory of the current organisation, or the CONFIG_DIR root when no organisation is in scope. */
    public static Path currentDir(Config config) {
        Integer orgId = OrgScope.currentOrgId();
        return orgId == null ? Path.of(config.getConfigDir()) : orgDir(config, orgId);
    }

    /** Moves pre-multi-organisation files (logo, rib, membership files, FFE/FIDE data) to their new location. */
    public static void migrateLegacyFiles(Config config) {
        Path root = Path.of(config.getConfigDir());
        Path target = orgDir(config, OrgScope.DEFAULT_ORG_ID);
        for (String name : LEGACY_ROOT_FILES) {
            moveIfMissing(root.resolve(name), target.resolve(name));
        }
        Path sharedLucene = root.resolve("lucene");
        Path legacyOrgLucene = target.resolve("lucene");
        moveIfMissing(legacyOrgLucene.resolve("manual-players.json"), sharedLucene.resolve("manual-players.json"));
        moveIfMissing(legacyOrgLucene.resolve("index"), sharedLucene.resolve("index"));
        for (String name : LEGACY_SHARED_FILES) {
            moveIfMissing(root.resolve(name), sharedDir(config).resolve(name));
        }
    }

    private static void moveIfMissing(Path source, Path target) {
        if (!Files.exists(source) || Files.exists(target)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            logger.info("Moved {} to {}", source, target);
        } catch (IOException e) {
            logger.warn("Cannot move {} to {}", source, target, e);
        }
    }
}
