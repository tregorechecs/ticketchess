package com.github.gcolin.player;

import com.github.gcolin.organisation.OrganisationFiles;
import com.github.gcolin.platform.Config;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the shared FFE/FIDE Lucene index (intended to run in a dedicated JVM). */
public final class LuceneIndexBuilder {

    public static final String REBUILD_ARG = "--rebuild-lucene-index";

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexBuilder.class);

    private LuceneIndexBuilder() {}

    /** Rebuilds {@code CONFIG_DIR/lucene/index} atomically via {@code index.build}. */
    public static void rebuild(Config config) throws IOException {
        Path root = Path.of(config.getConfigDir());
        Path shared = OrganisationFiles.sharedDir(config);
        Path luceneRoot = root.resolve("lucene");
        Path indexDir = luceneRoot.resolve("index");
        Path buildDir = luceneRoot.resolve("index.build");
        String mdbFile = shared.resolve("Data.mdb").toString();
        String fideFile = shared.resolve("players_list_xml.zip").toString();
        String manualPlayersFile = luceneRoot.resolve("manual-players.json").toString();

        if (Files.exists(buildDir)) {
            IOUtils.deleteDirectory(buildDir);
        }
        Files.createDirectories(buildDir);

        logger.info("Building Lucene index in {}", buildDir.toAbsolutePath());
        try (Directory directory = FSDirectory.open(buildDir)) {
            fill(directory, mdbFile, fideFile, manualPlayersFile, config);
        }

        Path backupDir = luceneRoot.resolve("index.old");
        if (Files.exists(backupDir)) {
            IOUtils.deleteDirectory(backupDir);
        }
        if (Files.exists(indexDir)) {
            moveReplace(indexDir, backupDir);
        }
        try {
            moveReplace(buildDir, indexDir);
        } catch (IOException e) {
            if (Files.exists(backupDir) && !Files.exists(indexDir)) {
                try {
                    moveReplace(backupDir, indexDir);
                } catch (IOException restoreError) {
                    e.addSuppressed(restoreError);
                }
            }
            throw e;
        }
        if (Files.exists(backupDir)) {
            IOUtils.deleteDirectory(backupDir);
        }
        logger.info("Lucene index ready at {}", indexDir.toAbsolutePath());
    }

    /** Fills an already-opened directory (used by unit tests with {@code ByteBuffersDirectory}). */
    public static void fill(
            Directory directory, String mdbFile, String fideFile, String manualPlayersFile, Config config)
            throws IOException {
        IndexWriterConfig writerConfig = new IndexWriterConfig(createAnalyzer());
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        // Keep the indexer process lean (shared host / Pi); flush to disk more often.
        writerConfig.setRAMBufferSizeMB(32);

        boolean loadtest = "true".equals(System.getProperty("loadtest"));

        try (IndexWriter writer = new IndexWriter(directory, writerConfig)) {
            boolean hasFFE = !loadtest && Files.exists(Path.of(mdbFile));
            boolean hasFide = !loadtest && Files.exists(Path.of(fideFile));

            if (hasFFE) {
                logger.info("load FFE db");
                boolean excludeAffTypeN = config != null
                        && Boolean.parseBoolean(config.getProperty("ffe.excludeAffTypeN", "false"));
                new FFELoader(excludeAffTypeN).load(writer, mdbFile);
            }

            if (hasFide) {
                logger.info("load Fide db");
                new FideLoader().load(writer, fideFile);
            }

            if (!hasFFE && !hasFide) {
                new TestLoader().load(writer);
            }

            mergeManualPlayers(writer, manualPlayersFile);

            writer.commit();
            logger.info("Indexation terminée");
        }
    }

    static Analyzer createAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(tokenizer);
                stream = new ASCIIFoldingFilter(stream);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    private static void mergeManualPlayers(IndexWriter writer, String manualPlayersFile) throws IOException {
        List<ManualPlayerEntry> manualPlayers = readManualPlayers(manualPlayersFile);
        if (manualPlayers.isEmpty()) {
            return;
        }

        List<ManualPlayerEntry> kept = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher indexSearcher = new IndexSearcher(reader);
            for (ManualPlayerEntry player : manualPlayers) {
                if (isImportedByFfeOrFide(player, indexSearcher)) {
                    logger.info("remove manual player {} because it is now imported", player.getKey());
                    continue;
                }
                writer.addDocument(manualPlayerToDocument(player));
                kept.add(player);
            }
        }

        if (kept.size() != manualPlayers.size()) {
            writeManualPlayers(manualPlayersFile, kept);
        }
    }

    private static boolean isImportedByFfeOrFide(ManualPlayerEntry player, IndexSearcher indexSearcher)
            throws IOException {
        String nrffe = trimToNull(player.getNrffe());
        if (nrffe != null) {
            Query nrffeQuery = new TermQuery(new Term("nrffe", nrffe));
            TopDocs byNrffe = indexSearcher.search(nrffeQuery, 1);
            if (byNrffe.scoreDocs.length > 0) {
                return true;
            }
        }

        String fide = trimToNull(player.getFide());
        if (fide != null && !"0".equals(fide)) {
            Query fideQuery = new TermQuery(new Term("fide", fide));
            TopDocs byFide = indexSearcher.search(fideQuery, 1);
            return byFide.scoreDocs.length > 0;
        }
        return false;
    }

    static Document manualPlayerToDocument(ManualPlayerEntry player) {
        Document doc = new Document();
        doc.add(new StringField("type", "joueur", Field.Store.YES));

        String nrffe = trimToNull(player.getNrffe());
        String fide = trimToNull(player.getFide());
        String name = defaultString(trimToNull(player.getName()));
        String firstname = defaultString(trimToNull(player.getFirstname()));

        doc.add(new StringField("nrffe", defaultString(nrffe), Field.Store.YES));
        doc.add(new StringField("fide", fide == null ? "0" : fide, Field.Store.YES));

        doc.add(new TextField("nom", name, Field.Store.YES));
        doc.add(new TextField("prenom", firstname, Field.Store.YES));
        doc.add(new TextField(
                "fullName",
                (firstname + " " + name + " " + defaultString(nrffe) + " " + defaultString(fide)).trim(),
                Field.Store.NO));

        doc.add(new StringField("birth", defaultString(trimToNull(player.getBirth())), Field.Store.YES));
        doc.add(new StringField("cat", defaultString(trimToNull(player.getCategory()), "SenM"), Field.Store.YES));
        doc.add(new StringField("affType", defaultString(trimToNull(player.getAffType()), "A"), Field.Store.YES));
        doc.add(new StringField(
                "fideCode", defaultString(trimToNull(player.getFideCode()), defaultString(fide)), Field.Store.YES));
        if (player.getFideTitre() != null && !player.getFideTitre().isEmpty()) {
            doc.add(new StringField("fideTitre", defaultString(trimToNull(player.getFideTitre())), Field.Store.YES));
        }
        doc.add(new StringField(
                "federation", defaultString(trimToNull(player.getFederation()), "FRA"), Field.Store.YES));
        doc.add(new StringField("club", defaultString(trimToNull(player.getClub())), Field.Store.YES));
        doc.add(new StringField("eloStd", defaultString(trimToNull(player.getEloStd()), "1399F"), Field.Store.YES));
        doc.add(new StringField(
                "eloRapide", defaultString(trimToNull(player.getEloRapide()), "1399F"), Field.Store.YES));
        doc.add(new StringField("eloBlitz", defaultString(trimToNull(player.getEloBlitz()), "1399F"), Field.Store.YES));
        return doc;
    }

    private static List<ManualPlayerEntry> readManualPlayers(String manualPlayersFile) throws IOException {
        File file = new File(manualPlayersFile);
        if (!file.exists() || file.length() == 0) {
            return new ArrayList<>();
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(text);
            List<ManualPlayerEntry> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                ManualPlayerEntry entry = fromManualPlayerJson(obj);
                entry.normalize();
                if (entry.getKey() != null && entry.getName() != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (RuntimeException e) {
            throw new IOException("cannot parse manual players JSON", e);
        }
    }

    private static void writeManualPlayers(String manualPlayersFile, List<ManualPlayerEntry> players)
            throws IOException {
        File file = new File(manualPlayersFile);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Map<String, ManualPlayerEntry> dedup = new LinkedHashMap<>();
        for (ManualPlayerEntry player : players) {
            player.normalize();
            if (player.getKey() != null) {
                dedup.put(player.getKey(), player);
            }
        }

        JSONArray array = new JSONArray();
        for (ManualPlayerEntry player : dedup.values()) {
            array.put(toManualPlayerJson(player));
        }
        Files.writeString(file.toPath(), array.toString(2) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static ManualPlayerEntry fromManualPlayerJson(JSONObject obj) {
        ManualPlayerEntry entry = new ManualPlayerEntry();
        entry.setNrffe(optString(obj, "nrffe"));
        entry.setFide(optString(obj, "fide"));
        entry.setName(optString(obj, "name"));
        entry.setFirstname(optString(obj, "firstname"));
        entry.setBirth(optString(obj, "birth"));
        entry.setCategory(optString(obj, "category"));
        entry.setAffType(optString(obj, "affType"));
        entry.setFideCode(optString(obj, "fideCode"));
        entry.setFideTitre(optString(obj, "fideTitre"));
        entry.setFederation(optString(obj, "federation"));
        entry.setClub(optString(obj, "club"));
        entry.setEloStd(optString(obj, "eloStd"));
        entry.setEloRapide(optString(obj, "eloRapide"));
        entry.setEloBlitz(optString(obj, "eloBlitz"));
        return entry;
    }

    private static JSONObject toManualPlayerJson(ManualPlayerEntry player) {
        JSONObject obj = new JSONObject();
        putIfPresent(obj, "nrffe", player.getNrffe());
        putIfPresent(obj, "fide", player.getFide());
        putIfPresent(obj, "name", player.getName());
        putIfPresent(obj, "firstname", player.getFirstname());
        putIfPresent(obj, "birth", player.getBirth());
        putIfPresent(obj, "category", player.getCategory());
        putIfPresent(obj, "affType", player.getAffType());
        putIfPresent(obj, "fideCode", player.getFideCode());
        putIfPresent(obj, "fideTitre", player.getFideTitre());
        putIfPresent(obj, "federation", player.getFederation());
        putIfPresent(obj, "club", player.getClub());
        putIfPresent(obj, "eloStd", player.getEloStd());
        putIfPresent(obj, "eloRapide", player.getEloRapide());
        putIfPresent(obj, "eloBlitz", player.getEloBlitz());
        return obj;
    }

    private static String optString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        String value = obj.optString(key, null);
        return value == null || value.isEmpty() ? null : value;
    }

    private static void putIfPresent(JSONObject obj, String key, String value) {
        if (value != null) {
            obj.put(key, value);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
