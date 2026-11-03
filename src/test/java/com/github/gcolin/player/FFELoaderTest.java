package com.github.gcolin.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FFELoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsClubsAndFiltersInvalidPlayers() throws Exception {
        Path file = tempDir.resolve("players.mdb");
        try (Database db = DatabaseBuilder.create(Database.FileFormat.V2003, file.toFile())) {
            Table clubs = new TableBuilder("CLUB")
                    .addColumn(c("Ref", DataType.LONG))
                    .addColumn(c("NrFFE", DataType.TEXT))
                    .addColumn(c("Nom", DataType.TEXT))
                    .addColumn(c("Commune", DataType.TEXT))
                    .addColumn(c("Ligue", DataType.TEXT))
                    .addColumn(c("Actif", DataType.TEXT))
                    .toTable(db);
            clubs.addRow(12, "C12", "Club de Test", "Paris", "IDF", "O");
            clubs.addRow(13, "C13", null, null, null, "N");

            Table joueurs = new TableBuilder("JOUEUR")
                    .addColumn(c("NrFFE", DataType.TEXT))
                    .addColumn(c("Nom", DataType.TEXT))
                    .addColumn(c("Prenom", DataType.TEXT))
                    .addColumn(c("Elo", DataType.INT))
                    .addColumn(c("Fide", DataType.TEXT))
                    .addColumn(c("Rapide", DataType.INT))
                    .addColumn(c("RapideFide", DataType.TEXT))
                    .addColumn(c("Blitz", DataType.INT))
                    .addColumn(c("BlitzFide", DataType.TEXT))
                    .addColumn(c("NeLe", DataType.SHORT_DATE_TIME))
                    .addColumn(c("Ref", DataType.LONG))
                    .addColumn(c("FideCode", DataType.TEXT))
                    .addColumn(c("Cat", DataType.TEXT))
                    .addColumn(c("FideTitre", DataType.TEXT))
                    .addColumn(c("Federation", DataType.TEXT))
                    .addColumn(c("AffType", DataType.TEXT))
                    .addColumn(c("ClubRef", DataType.LONG))
                    .toTable(db);
            joueurs.addRow("N1", "Nom", "Prenom", (short) 1800, "K", (short) 1700, "R", (short) 1600, "B",
                    LocalDateTime.of(1990, 1, 2, 0, 0), 99, " FIDE ", "S", "GM", "FRA", "A", 12);
            joueurs.addRow("N2", "Nom", "Prenom", (short) 1200, "", (short) 0, "", (short) 0, "",
                    LocalDateTime.of(1991, 1, 2, 0, 0), 0, null, "S", "c", "FRA", "N", 12);
            joueurs.addRow(null, "Nom", "Prenom", (short) 1200, "", (short) 0, "", (short) 0, "",
                    LocalDateTime.of(1991, 1, 2, 0, 0), 0, null, "S", "cf", "FRA", "A", 12);
            joueurs.addRow("N3", "Nom", "Prenom", (short) 1200, "", (short) 0, "", (short) 0, "",
                    LocalDateTime.of(1991, 1, 2, 0, 0), 0, null, "S", "", "BEL", "A", 12);
        }

        ByteBuffersDirectory directory = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
            new FFELoader(false).load(writer, file.toString());
            writer.commit();
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals(3, reader.numDocs());
            IndexSearcher searcher = new IndexSearcher(reader);
            Document player = searcher.storedFields().document(1);
            assertEquals("Club de Test", player.get("club"));
            assertEquals("GM", player.get("fideTitre"));
        }

        ByteBuffersDirectory filteredDirectory = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(filteredDirectory, new IndexWriterConfig())) {
            new FFELoader(true).load(writer, file.toString());
            writer.commit();
        }
        try (DirectoryReader reader = DirectoryReader.open(filteredDirectory)) {
            assertEquals(2, reader.numDocs());
        }
    }

    private static ColumnBuilder c(String name, DataType type) {
        return new ColumnBuilder(name).setType(type);
    }
}
