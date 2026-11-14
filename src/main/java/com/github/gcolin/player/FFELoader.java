package com.github.gcolin.player;

import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FFELoader {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean excludeAffTypeN;

    public FFELoader() {
        this(false);
    }

    public FFELoader(boolean excludeAffTypeN) {
        this.excludeAffTypeN = excludeAffTypeN;
    }

    private Document toDocument(Row row, Map<Integer, String> clubNames) {
        Document doc = new Document();

        String nrffe = row.getString("NrFFE");
        String nom = row.getString("Nom");
        String prenom = row.getString("Prenom");
        String eloStd = row.getShort("Elo") + row.getString("Fide");
        String eloRapide = row.getShort("Rapide") + row.getString("RapideFide");
        String eloBlitz = row.getShort("Blitz") + row.getString("BlitzFide");
        LocalDateTime birthDate = row.getLocalDateTime("NeLe");

        if (nom == null
                || prenom == null
                || nrffe == null
                || (excludeAffTypeN && "N".equals(row.getString("AffType")))) {
            return null;
        }

        String fullName = prenom + " " + nom + " " + nrffe;

        doc.add(new StringField("type", "joueur", Field.Store.YES));

        Object refObj = row.get("Ref");
        if (refObj instanceof Number) {
            int ref = ((Number) refObj).intValue();
            if (ref != 0) {
                doc.add(new StringField("refffe", String.valueOf(ref), Field.Store.YES));
            }
        }

        String fideCode = row.getString("FideCode");
        doc.add(new StringField("fide", fideCode == null ? "0" : fideCode.trim(), Field.Store.YES));
        doc.add(new StringField("nrffe", nrffe, Field.Store.YES));

        doc.add(new TextField("nom", nom, Field.Store.YES));
        doc.add(new TextField("prenom", prenom, Field.Store.YES));
        doc.add(new TextField("fullName", fullName, Field.Store.NO));
        doc.add(new StringField("birth", birthDate.toString(), Field.Store.YES));
        doc.add(new StringField("cat", row.getString("Cat"), Field.Store.YES));
        if (row.getString("FideCode") != null) {
            doc.add(new StringField("fideCode", row.getString("FideCode"), Field.Store.YES));
        }
        if (row.getString("FideTitre") != null) {
            String titre = row.getString("FideTitre").trim();
            if (titre.length() > 0 && !"c".equals(titre) && !"cf".equals(titre)) {
                doc.add(new StringField("fideTitre", titre, Field.Store.YES));
            }
        }
        if (row.getString("Federation") != null) {
            doc.add(new StringField("federation", row.getString("Federation"), Field.Store.YES));
        }

        if (!"FRA".equals(row.getString("Federation"))) {
            return null;
        }
        doc.add(new StringField("affType", row.getString("AffType"), Field.Store.YES));

        int clubRef = row.getInt("ClubRef");
        doc.add(new IntField("clubRef", clubRef, Field.Store.YES));
        String clubName = clubNames.get(clubRef);
        if (clubName != null) {
            doc.add(new StringField("club", clubName, Field.Store.YES));
        }

        doc.add(new StringField("eloStd", eloStd, Field.Store.YES));
        doc.add(new StringField("eloRapide", eloRapide, Field.Store.YES));
        doc.add(new StringField("eloBlitz", eloBlitz, Field.Store.YES));

        return doc;
    }

    private Document clubToDocument(Row row) {
        Document doc = new Document();

        int ref = row.getInt("Ref");
        String nrffe = row.getString("NrFFE");
        String nom = row.getString("Nom");
        String commune = row.getString("Commune");
        String ligue = row.getString("Ligue");

        if (nom == null) {
            return null;
        }

        doc.add(new StringField("type", "club", Field.Store.YES));

        doc.add(new IntField("clubRef", ref, Field.Store.YES));
        doc.add(new StringField("nrffe", nrffe == null ? "" : nrffe, Field.Store.YES));

        doc.add(new TextField("nom", nom, Field.Store.YES));
        doc.add(new TextField("ligue", ligue, Field.Store.YES));
        doc.add(new TextField("commune", commune == null ? "" : commune, Field.Store.YES));

        String clubSearch = nom + " " + (commune == null ? "" : commune);
        doc.add(new TextField("clubSearch", clubSearch, Field.Store.NO));

        doc.add(new StringField("actif", row.getString("Actif"), Field.Store.YES));

        return doc;
    }

    public void load(IndexWriter writer, String mdb) throws IOException {
        int count = 0;
        try (Database db = new DatabaseBuilder(new File(mdb)).setReadOnly(true).open()) {

            Table clubTable = db.getTable("CLUB");
            Map<Integer, String> clubNames = new HashMap<>();

            for (Row row : clubTable) {
                Document doc = clubToDocument(row);
                if (doc != null) {
                    clubNames.put(row.getInt("Ref"), row.getString("Nom"));
                    writer.addDocument(doc);
                }
            }

            Table joueurs = db.getTable("JOUEUR");
            for (Row row : joueurs) {
                Document doc = toDocument(row, clubNames);
                if (doc != null) {
                    writer.addDocument(doc);
                    count++;
                    if (count % 50_000 == 0) {
                        logger.info("Indexés : " + count);
                    }
                }
            }
        }
    }
}
