package com.github.gcolin.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PropertiesFileUpdater {

    private PropertiesFileUpdater() {}

    public static void update(Path file, Map<String, String> updates, Set<String> secretKeys) throws IOException {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        List<String> lines = Files.exists(file)
                ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (secretKeys != null && secretKeys.contains(key) && value.isBlank()) {
                continue;
            }
            if (!replaceExisting(lines, key, value)) {
                missing.add(key + "=" + value);
            }
        }
        if (!missing.isEmpty()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.addAll(missing);
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Deletes assignment lines for the given keys (used when settings move to the organisation table). */
    public static void removeKeys(Path file, Set<String> keys) throws IOException {
        if (keys == null || keys.isEmpty() || file == null || !Files.exists(file)) {
            return;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        List<String> kept = new ArrayList<>(lines.size());
        for (String line : lines) {
            String key = propertyKey(line);
            if (key == null || !keys.contains(key)) {
                kept.add(line);
            }
        }
        Files.write(file, kept, StandardCharsets.UTF_8);
    }

    private static String propertyKey(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        return trimmed.substring(0, eq).trim();
    }

    private static boolean replaceExisting(List<String> lines, String key, String value) {
        Pattern pattern = Pattern.compile("^(\\s*#?\\s*)" + Pattern.quote(key) + "(\\s*=\\s*)(.*)$");
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = pattern.matcher(lines.get(i));
            if (matcher.matches()) {
                lines.set(i, key + "=" + value);
                return true;
            }
        }
        return false;
    }
}
