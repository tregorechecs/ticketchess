package com.github.gcolin.player;

import com.github.gcolin.platform.Config;
import com.github.gcolin.player.Club;
import com.github.gcolin.player.ManualPlayerEntry;
import com.github.gcolin.player.Player;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneDb {

    /** Enough for FFE+FIDE rebuild with a 32 MB Lucene RAM buffer; keep low for Pi hosts. */
    static final String INDEXER_HEAP = "-Xmx384m";
    private static final long INDEXER_TIMEOUT_MINUTES = 30;
    private static final int INDEXER_OUTPUT_CAP = 64_000;

    private IndexSearcher searcher;
    private Analyzer analyzer;
    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private File dir = new File("lucene/index");
    String mdbFile = "Data.mdb";
    String fideFile = "players_list_xml.zip";
    String manualPlayersFile = "lucene/manual-players.json";
    private Pattern decimal = Pattern.compile("\\d+");
    private Directory directory;
    private Config config;

    public void setConfig(Config config) {
        this.config = config;
    }

    public synchronized void downloadAndExtractFFeDb() throws IOException {
        String url = "https://www.echecs.asso.fr/Papi/PapiData.zip";
        File dbDir = new File(mdbFile).getAbsoluteFile().getParentFile();
        // Stream unzip from the URL so we never keep PapiData.zip + Data.mdb in RAM/disk together.
        logger.info("download and extract {}", url);
        IOUtils.downloadAndUnzip(url, dbDir.getAbsolutePath());
    }

    public synchronized void downloadFideDB() throws IOException {
        String url = "https://ratings.fide.com/download/players_list_xml.zip";
        File dbDir = new File(mdbFile).getAbsoluteFile().getParentFile();
        String zipPath = new File(dbDir, "players_list_xml.zip").getAbsolutePath();
        File partial = new File(zipPath + ".partial");
        partial.delete();
        logger.info("download {}", url);
        IOUtils.downloadFile(url, partial.getAbsolutePath());
        Files.move(partial.toPath(), Path.of(zipPath), StandardCopyOption.REPLACE_EXISTING);
    }

    public File getMdbFile() {
        return new File(mdbFile);
    }

    public File getFideFile() {
        return new File(fideFile);
    }

    public File getManualPlayersFile() {
        return new File(manualPlayersFile);
    }

    /** Shared FFE/FIDE index for all organisations: {@code CONFIG_DIR/lucene} + {@code CONFIG_DIR/shared}. */
    public void init() {
        Path root = Path.of(config.getConfigDir());
        Path shared = com.github.gcolin.organisation.OrganisationFiles.sharedDir(config);
        dir = root.resolve("lucene").resolve("index").toFile();
        manualPlayersFile = root.resolve("lucene").resolve("manual-players.json").toString();
        mdbFile = shared.resolve("Data.mdb").toString();
        fideFile = shared.resolve("players_list_xml.zip").toString();
    }

    private void ensureLoaded() throws IOException {
        if (searcher == null) {
            load(false);
        }
    }

    public synchronized void load(boolean refresh) throws IOException {
        close();
        logger.info("load LuceneDb from {}", dir.getAbsolutePath());

        if ("true".equals(System.getProperty("test"))) {
            directory = new ByteBuffersDirectory();
            LuceneIndexBuilder.fill(directory, mdbFile, fideFile, manualPlayersFile, config);
            openForRead();
            return;
        }

        if (refresh || !indexExistsOnDisk()) {
            rebuildIndexOutOfProcess();
        }

        directory = FSDirectory.open(Paths.get(dir.getAbsolutePath()));
        try {
            openForRead();
        } catch (IOException e) {
            logger.warn("cannot open lucene, rebuilding index out of process", e);
            closeDirectoryQuietly();
            rebuildIndexOutOfProcess();
            directory = FSDirectory.open(Paths.get(dir.getAbsolutePath()));
            openForRead();
        }
    }

    private boolean indexExistsOnDisk() throws IOException {
        if (!dir.exists()) {
            return false;
        }
        try (Directory probe = FSDirectory.open(dir.toPath())) {
            return DirectoryReader.indexExists(probe);
        }
    }

    private void rebuildIndexOutOfProcess() throws IOException {
        Path javaBin = resolveJavaBinary();
        Path jar = resolveApplicationJar();
        String configDir = config != null ? config.getConfigDir() : System.getProperty("CONFIG_DIR", ".");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin.toString(),
                INDEXER_HEAP,
                "-DCONFIG_DIR=" + configDir,
                "-jar",
                jar.toString(),
                LuceneIndexBuilder.REBUILD_ARG);
        pb.environment().put("CONFIG_DIR", configDir);
        pb.redirectErrorStream(true);
        logger.info("Starting Lucene indexer: {} {} -jar {} {}", javaBin, INDEXER_HEAP, jar, LuceneIndexBuilder.REBUILD_ARG);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[lucene-indexer] {}", line);
                    if (output.length() < INDEXER_OUTPUT_CAP) {
                        output.append(line).append('\n');
                    }
                }
            }
            boolean finished = process.waitFor(INDEXER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Lucene indexer timed out after " + INDEXER_TIMEOUT_MINUTES + " minutes");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IOException("Lucene indexer failed with exit " + exit + ":\n" + output);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Lucene indexer", e);
        }
    }

    static Path resolveJavaBinary() {
        String javaHome = System.getProperty("java.home");
        Path bin = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");
        if (Files.isExecutable(bin) || Files.exists(bin)) {
            return bin;
        }
        return Path.of("java");
    }

    static Path resolveApplicationJar() throws IOException {
        URL location = LuceneDb.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            try {
                URI uri = location.toURI();
                Path path = Paths.get(uri);
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                    return path.toAbsolutePath().normalize();
                }
            } catch (URISyntaxException | IllegalArgumentException e) {
                loggerFallback(e);
            }
        }
        Path fallback = Path.of("/app/app.jar");
        if (Files.isRegularFile(fallback)) {
            return fallback;
        }
        throw new IOException("Cannot locate application jar for Lucene indexer (looked at code source and /app/app.jar)");
    }

    private static void loggerFallback(Exception e) {
        LoggerFactory.getLogger(LuceneDb.class).debug("Cannot resolve jar from code source", e);
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }

    private void openForRead() throws IOException {
        DirectoryReader reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
        analyzer = LuceneIndexBuilder.createAnalyzer();
    }

    private void closeDirectoryQuietly() {
        if (directory != null) {
            try {
                directory.close();
            } catch (IOException ignored) {
                // best effort before rebuild
            }
            directory = null;
        }
    }

    public void close() throws IOException {
        if (searcher != null) {
            searcher.getIndexReader().close();
            searcher = null;
        }
        if (analyzer != null) {
            analyzer.close();
            analyzer = null;
        }
        closeDirectoryQuietly();
    }

    public synchronized List<Club> searchClub(String query, int nb) throws ParseException, IOException {
        ensureLoaded();
        QueryParser clubParser = new QueryParser("clubSearch", analyzer);
        clubParser.setDefaultOperator(QueryParser.Operator.AND);

        Query clubQuery = clubParser.parse(query);

        BooleanQuery.Builder clubBool = new BooleanQuery.Builder();

        clubBool.add(clubQuery, BooleanClause.Occur.MUST);
        Query finalClubQuery = clubBool.build();

        TopDocs clubHits = searcher.search(finalClubQuery, nb);

        List<Club> clubs = new ArrayList<>();

        for (ScoreDoc sd : clubHits.scoreDocs) {

            Document doc = searcher.storedFields().document(sd.doc);
            clubs.add(docToClub(doc, sd.score));
        }
        return clubs;
    }

    public synchronized Club searchClub(String ref) throws IOException {
        ensureLoaded();
        Query clubQuery = IntPoint.newExactQuery("clubRef", Integer.parseInt(ref));

        TopDocs hits = searcher.search(clubQuery, 1);

        if (hits.scoreDocs.length > 0) {
            Document doc = searcher.storedFields().document(hits.scoreDocs[0].doc);
            return docToClub(doc, hits.scoreDocs[0].score);
        }
        return null;
    }

    public synchronized List<Player> searchJoueursByClub(int ref) throws ParseException, IOException {
        ensureLoaded();
        Query nrffeQuery = IntPoint.newExactQuery("clubRef", ref);
        TopDocs hits = searcher.search(nrffeQuery, 200);
        List<Player> players = new ArrayList<>();
        if (hits.scoreDocs.length > 0) {
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                players.add(docToJoueur(doc, sd.score));
            }
        }
        return players;
    }

    public synchronized Player searchJoueur(String nrFFE) throws ParseException, IOException {
        ensureLoaded();
        if (nrFFE == null || nrFFE.isBlank()) {
            return null;
        }
        String ref = nrFFE.trim();
        if (decimal.matcher(ref).matches()) {
            // Numeric refs are usually FIDE ids; fall back to nrffe for odd licence formats.
            Player byFide = searchJoueurByField("fide", ref);
            if (byFide != null) {
                return byFide;
            }
            return searchJoueurByField("nrffe", ref);
        }
        Query nrffeQuery = new TermQuery(new Term("nrffe", ref));
        TopDocs hits = searcher.search(nrffeQuery, 1);
        if (hits.scoreDocs.length > 0) {
            Document doc = searcher.storedFields().document(hits.scoreDocs[0].doc);
            return docToJoueur(doc, hits.scoreDocs[0].score);
        }
        return null;
    }

    public synchronized Player searchJoueurByNrffe(String nrffe) throws IOException {
        ensureLoaded();
        String value = trimToNull(nrffe);
        if (value == null) {
            return null;
        }
        return searchJoueurByField("nrffe", value);
    }

    public synchronized Player searchJoueurByFide(String fide) throws IOException {
        ensureLoaded();
        String value = trimToNull(fide);
        if (value == null) {
            return null;
        }
        return searchJoueurByField("fide", value);
    }

    private Player searchJoueurByField(String field, String value) throws IOException {
        Query query = new TermQuery(new Term(field, value));
        TopDocs hits = searcher.search(query, 1);
        if (hits.scoreDocs.length > 0) {
            Document doc = searcher.storedFields().document(hits.scoreDocs[0].doc);
            return docToJoueur(doc, hits.scoreDocs[0].score);
        }
        return null;
    }

    public synchronized List<Player> searchJoueur(String query, int nb, Integer club)
            throws ParseException, IOException {
        ensureLoaded();
        QueryParser playerParser = new QueryParser("fullName", analyzer);
        playerParser.setDefaultOperator(QueryParser.Operator.AND);

        Query playerQuery = playerParser.parse(query);

        BooleanQuery.Builder playerBool = new BooleanQuery.Builder();

        playerBool.add(playerQuery, BooleanClause.Occur.MUST);
        if (club != null) {
            playerBool.add(IntPoint.newExactQuery("clubRef", club), BooleanClause.Occur.FILTER);
        }

        Query finalClubQuery = playerBool.build();

        TopDocs clubHits = searcher.search(finalClubQuery, nb);

        List<Player> clubs = new ArrayList<>();

        for (ScoreDoc sd : clubHits.scoreDocs) {

            Document doc = searcher.storedFields().document(sd.doc);
            clubs.add(docToJoueur(doc, sd.score));
        }
        return clubs;
    }

    private Player docToJoueur(Document doc, float score) {
        Player joueur = new Player();
        joueur.setNrffe(doc.get("nrffe"));
        joueur.setName(doc.get("nom"));
        joueur.setFirstname(doc.get("prenom"));
        if (joueur.getFirstname() == null) {
            joueur.setFirstname("");
        }
        joueur.setClubRef(doc.get("clubRef"));
        joueur.setRating(doc.get("eloStd"));
        joueur.setRapidRating(doc.get("eloRapide"));
        joueur.setBlitzRating(doc.get("eloBlitz"));
        joueur.setBirthDate(doc.get("birth"));
        joueur.setCategory(doc.get("cat"));
        joueur.setAffType(doc.get("affType"));
        joueur.setFideCode(doc.get("fideCode"));
        joueur.setFideTitre(doc.get("fideTitre"));
        joueur.setFederation(doc.get("federation"));
        joueur.setClub(doc.get("club"));
        joueur.setFide(doc.get("fide"));
        joueur.setRefffe(doc.get("refffe"));
        return joueur;
    }

    private Club docToClub(Document doc, float score) {
        Club club = new Club();
        club.setRef(doc.get("clubRef"));
        club.setNrffe(doc.get("nrffe"));
        club.setNom(doc.get("nom"));
        club.setCommune(doc.get("commune"));
        club.setActif(doc.get("actif"));
        club.setLigue(doc.get("ligue"));
        club.setScore(score);
        return club;
    }

    public synchronized List<ManualPlayerEntry> getManualPlayers() throws IOException {
        return readManualPlayersInternal();
    }

    public synchronized void addManualPlayer(ManualPlayerEntry player) throws IOException {
        validateManualPlayer(player);
        List<ManualPlayerEntry> players = readManualPlayersInternal();

        String key = player.getKey();
        players.removeIf(entry -> key.equals(entry.getKey()));
        players.add(player);

        writeManualPlayersInternal(players);
    }

    public synchronized boolean removeManualPlayer(String key) throws IOException {
        String normalizedKey = trimToNull(key);
        if (normalizedKey == null) {
            return false;
        }
        List<ManualPlayerEntry> players = readManualPlayersInternal();
        boolean removed = players.removeIf(entry -> normalizedKey.equals(entry.getKey()));
        if (removed) {
            writeManualPlayersInternal(players);
        }
        return removed;
    }

    private void validateManualPlayer(ManualPlayerEntry player) {
        if (trimToNull(player.getName()) == null) {
            throw new IllegalArgumentException("name is required");
        }
        if (trimToNull(player.getNrffe()) == null && trimToNull(player.getFide()) == null) {
            throw new IllegalArgumentException("nrffe or fide is required");
        }
        player.normalize();
    }

    private List<ManualPlayerEntry> readManualPlayersInternal() throws IOException {
        File file = getManualPlayersFile();
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

    private void writeManualPlayersInternal(List<ManualPlayerEntry> players) throws IOException {
        File file = getManualPlayersFile();
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
