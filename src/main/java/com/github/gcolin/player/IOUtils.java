package com.github.gcolin.player;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class IOUtils {

    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 60_000;
    private static final int READ_TIMEOUT_MS = 300_000;

    public static void downloadFile(String fileURL, String savePath) throws IOException {
        URLConnection connection = openDownload(fileURL);
        try (InputStream in = new BufferedInputStream(connection.getInputStream(), STREAM_BUFFER_SIZE);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(savePath), STREAM_BUFFER_SIZE)) {
            in.transferTo(out);
        } finally {
            disconnectQuietly(connection);
        }
    }

    /** Streams a remote zip and extracts entries to {@code destDir} without keeping the zip on disk. */
    public static void downloadAndUnzip(String fileURL, String destDir) throws IOException {
        URLConnection connection = openDownload(fileURL);
        try (InputStream in = new BufferedInputStream(connection.getInputStream(), STREAM_BUFFER_SIZE);
                ZipInputStream zis = new ZipInputStream(in)) {
            extractZipStream(zis, destDir);
        } finally {
            disconnectQuietly(connection);
        }
    }

    public static void deleteDirectory(Path dir) throws IOException {
        if (Files.notExists(dir)) {
            return;
        }

        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()) // fichiers avant dossiers
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    public static void unzip(String zipFilePath, String destDir) throws IOException {
        try (ZipInputStream zis =
                new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFilePath), STREAM_BUFFER_SIZE))) {
            extractZipStream(zis, destDir);
        }
    }

    private static URLConnection openDownload(String fileURL) throws IOException {
        URL url = URI.create(fileURL).toURL();
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        if (connection instanceof HttpURLConnection http) {
            http.setInstanceFollowRedirects(true);
            // Avoid opaque gzip buffering of already-compressed archives in some JDKs.
            http.setRequestProperty("Accept-Encoding", "identity");
        }
        return connection;
    }

    private static void disconnectQuietly(URLConnection connection) {
        if (connection instanceof HttpURLConnection http) {
            http.disconnect();
        }
    }

    private static void extractZipStream(ZipInputStream zis, String destDir) throws IOException {
        File dir = new File(destDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            File newFile = newFile(dir, entry);

            if (entry.isDirectory()) {
                newFile.mkdirs();
            } else {
                File parent = newFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (OutputStream fos =
                        new BufferedOutputStream(new FileOutputStream(newFile), STREAM_BUFFER_SIZE)) {
                    zis.transferTo(fos);
                }
            }

            zis.closeEntry();
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry en dehors du dossier cible : " + zipEntry.getName());
        }

        return destFile;
    }
}
